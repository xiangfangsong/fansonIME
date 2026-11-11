# 数据与隐私

基线见 [入口](README.md)。数据实现主要位于 [`LocalStore.kt`](../../app/src/main/java/com/fanson/ime/data/LocalStore.kt)、[`PronunciationRepository.kt`](../../app/src/main/java/com/fanson/ime/data/PronunciationRepository.kt) 与 [`tools/`](../../tools)。

## 存储分层与资源准备

| 层次 | 路径/文件 | 使用方式 |
| --- | --- | --- |
| 固定上游记录 | [`resources.lock.json`](../../resources.lock.json) | 记录 revision、URL、原始文件 SHA-256，以及原字根字体信息 |
| 原始数据 | [`third_party/`](../../third_party) | 保留码表、OpenCC、Unicode/CLDR、英文词频、读音及许可资料 |
| 随 APK 分发的产物 | [`app/src/main/assets/`](../../app/src/main/assets) | SQLite、JSON、TSV、词表、字根字体及许可文件 |
| 产物元数据 | [`data_info.json`](../../app/src/main/assets/data_info.json) | 产物哈希、数量及 revision；运行时用其中哈希准备 SQLite 副本 |
| 用户数据 | `context.noBackupFilesDir/personal.db` | 可写 SQLite，保存设置、手动短语、五笔衰减词频、emoji 次数、剪贴板历史 |
| 字典运行副本 | `noBackupFilesDir/wubi-<哈希前12位>.db`、`pronunciation-<哈希前12位>.db` | 只读打开，由 assets 复制而来，与个人数据分开 |

两个字典仓库都在目标文件不存在时：复制到固定临时文件 → 计算完整 SHA-256 → 与元数据比对 → rename 为带哈希文件名 → 只读打开。已有目标文件会直接打开，目前不重新计算其哈希；也没有自动清除旧哈希副本或损坏副本自愈。

这些校验用于资源一致性，不是密码学意义上的独立信任来源：assets 和元数据同包分发。不要通过手改哈希绕过数据错误。

## 五笔库与候选查询

生成脚本创建的实际结构为：

```sql
CREATE TABLE entries(
    code TEXT NOT NULL,
    text TEXT NOT NULL,
    weight INTEGER NOT NULL,
    stem TEXT NOT NULL,
    PRIMARY KEY(code,text)
);
CREATE INDEX entries_order ON entries(code,weight DESC,text);
PRAGMA user_version=1;
```

`weight` 是 SQLite 64 位整数，已有值超过 32 位范围；不要改成 Int 保存词频。`stem` 保留上游取码字段，当前运行时候选查询不读取它，也没有把它接入自动生成短语编码的界面。

`WubiRepository.lookup(code)` 限制 `[a-y]{1,4}`，绑定 SQL 参数，按如下顺序合并：

1. 当前编码的手动短语，按 `text` 排序，优先加入。
2. 内置精确候选，按 `weight DESC,text` 查询，SQL 最多 200 行；按文字去重，保留先出现的条目。
3. 编码不足 4 位且合并结果少于 40 项时，再查 `code > prefix AND code < prefix+'z'`。按 `length(code),weight DESC,text` 取至多 80 行，继续去重，补到总数 40 项就停止；这些候选 `exact=false`。

因此“40”只是前缀补足目标，不是所有候选的统一上限；手动短语没有同样的数量限制，精确查询也可使总数超过 40。UI 展开页使用已查出的同一快照，并不继续从数据库加载下一页。短语目前只参与当前码的精确匹配，不参与前缀补全。

0.4.0 开启学习时，上述基础查询后最多补回当前输入码下 64 个已学习的有效内置来源，再于简繁转换后按三层内的有效分数稳定排序。历史来源必须通过绑定 SQL 核对，不直接生成候选；关闭开关、隐私字段和内部搜索仍走原查询。完整键、截断边界与算法见 [五笔词频说明](WUBI_FREQUENCY.md)。

## 读音与简繁

读音库结构：

```sql
CREATE TABLE readings(
    text TEXT PRIMARY KEY,
    pronunciations TEXT NOT NULL
) WITHOUT ROWID;
PRAGMA user_version=1;
```

多条读音以换行分隔。`PronunciationRepository` 仅在 worker 查询，使用访问顺序 `LinkedHashMap` 缓存最多 512 条结果，包含缺失提示，关闭时清空；不保存查读音历史。

`PronunciationDictionary` 先查完整字词，去空、去重后用 ` / ` 展示。完整记录缺失时，单字显示“暂无读音”；多字文本按 Unicode 码点逐字查，明确加“逐字：”，不组合穷举多音字读法。生成阶段补充繁体词组别名，避免“銀行”退化为不消歧的逐字结果。

`TraditionalConverter` 读取 `traditional.tsv` 的 `原文<TAB>替代1 替代2`。整段文本命中映射时保留全部替代结果；否则从左向右最长匹配，分段转换只选各段第一个替代，不做组合爆炸。未命中字按码点原样保留。候选转换后按 `(text,code,exact)` 稳定去重。这里使用 OpenCC 数据自行转换，没有引入 OpenCC 原生库。

## 个人数据库

`LocalStore` 直接打开 SQLite，启动时启用 `PRAGMA secure_delete=ON` 并创建下表。当前 `db.version=2`。schema 初始化由进程公共锁保护；事务建旧表，首次低于 1 时保留原删除旧 `favorites` 的处理，低于 2 时仅新增 `wubi_frequency` 并升至 2，不清旧数据。启用 WAL 以减少隐藏批写阻塞其他连接读取；仍没有 SQLiteOpenHelper 或通用迁移框架。version 1 合成旧库保留验证见本版记录。

| 表 | 字段与约束 | 写入及清除规则 |
| --- | --- | --- |
| `preferences` | `key TEXT PRIMARY KEY,value TEXT NOT NULL` | 所有值存字符串；由读取方指定默认值 |
| `phrases` | `code,text` 非空，复合主键 | 手动添加；码为 a～y 1～4 位，文字非空白且 Kotlin `length<=200`；支持按码/文字删除 |
| `clipboard_history` | `id INTEGER PRIMARY KEY AUTOINCREMENT,text TEXT NOT NULL UNIQUE` | 非空白、非敏感文本入库；事务内先删重复、再插入、仅留最大 10 个 id；读取倒序 |
| `emoji_usage` | `text TEXT PRIMARY KEY,uses INTEGER NOT NULL,last_used INTEGER NOT NULL` | 事务内累加次数；`last_used=当前最大值+1`，是顺序号，不是时间戳 |

新增 `wubi_frequency(input_code,source_code,source_text,text,score,updated_at,custom)`，前四字段为复合主键；score 为 REAL，时间为 INTEGER 毫秒。最多两万条内存记录，模型及批快照由进程共享的 WubiFrequencyStore 维护，后台只在真实隐藏键盘时保存日常变化；显式清空/删除是立即持久化的例外。详细结构、生命周期及 WAL 边界见 [词频说明](WUBI_FREQUENCY.md)。

各公开数据库操作用 `@Synchronized` 保护同一个 `LocalStore` 实例；Activity 和服务各开一个实例，不能依赖此注解获得跨实例的业务原子性。复合操作需要数据库事务。

原 `clearPersonalData()` 联合清空已移除。设置的三个独立确认分别通过共享词频 I/O 队列清短语、emoji 次数或五笔学习，保留设置和剪贴板；短语删除与相关失效学习在同一事务处理，无联合 VACUUM。清词频提升代数，旧反馈不再计入，串行保存不能复活清空数据。`clearClipboardHistory()` 仅清本地历史；面板“一键清空”另外调用系统剪贴板清除。`secure_delete` 与无备份配置不等于文件加密。

### 设置键契约

来源为 `LanguagePreferences`、`MainActivity`、`readSettings()` 和 `onStartInput()`，新增或改名要同步写入与读取方。语言关联键通过 `LocalStore.putAll()` 在同一 SQLite 事务保存，不更改数据库版本或现有表。

| key | 默认值 | 取值与应用时机 |
| --- | --- | --- |
| `default_language` | `wubi86` | `english / wubi86 / korean`；设置中的默认语言；修改时与当前首选、回切目标在同一事务内保存；未知值回退五笔 |
| `input_language` | `default_language` | 新输入框读取；主动切换成功或五笔 Shift 进入英文时保存；旧安装保留原值，未知值回退五笔；字段自动覆盖不保存 |
| `last_non_english_language` | 无记录 | 最近成功主动选择或离开的非英语语言；英语短按优先返回它，缺失/未知/误存英语时用非英语默认语言，默认也是英语则回退五笔；修改默认语言时重设 |
| `wubi_enter_action` | `commit_code` | `commit_code / clear_code / first_candidate`；读取设置后在 worker 更新；未知值回退默认 |
| `wubi_frequency` | `true` | 仅五笔本地衰减排序，未知值回退开启；关闭恢复旧固定查询并停止学习，保留已有记录；隐私和搜索强制禁用 |
| `auto_unique` | `true` | 四码唯一提交，读取后在 worker 更新 |
| `fifth_commit` | `true` | 第五码提交，读取后在 worker 更新 |
| `haptic` | `false` | 三语共用轻震动开关，关闭保留已选强度 |
| `haptic_strength` | `50` | 0–100 的整数字符串；缺失/非法值回退 50，超界夹到范围；0 不振动；触摸松手保存，无障碍/方向键单步保存；下次键盘显示读取并缓存效果。只用现有 preferences 表，无 schema 迁移 |
| `roots` | `false` | 字根简要提示与长按卡片共用开关 |
| `number_row` | `true` | 三种语言共用数字行 |
| `symbol_auto_return` | `true` | “输入符号后返回键盘”；仅符号网格输入后的自动返回，关闭后连续输入并保留分类；不影响 emoji 或符号页辅助按键 |
| `theme` | `system` | `system / light / dark`；创建 Palette；设置页更改后重建页面 |
| `key_height` | `48` | 字符串整数，解析失败回退 48，限制 40～64 dp；横屏实际高度最多 40 dp |

服务在 `onStartInput/onStartInputView` 读取设置。`traditional`、`spelling`、大小写、面板、读音和搜索词不在 `preferences`，不要误以为它们重启后也保持。

## 资源生成与复现

普通构建检查已有产物，不自动运行生成器。需要重建数据时，在仓库根目录按依赖顺序执行：

```powershell
python tools\prepare_data.py
python tools\prepare_completion_data.py
python tools\prepare_pronunciation_data.py
python tools\verify_assets.py
```

| 脚本 | 输入 → 产物 | 联网/环境注意事项 |
| --- | --- | --- |
| `prepare_data.py` | Rime、Unicode/CLDR → 五笔 SQLite、emoji JSON、基础许可和元数据 | 锁文件与所有源文件齐全时离线；缺源文件会尝试下载，锁文件缺失会选择 revision 并下载。它不是无条件离线脚本 |
| `prepare_completion_data.py` | OpenCC、FrequencyWords → 简繁 TSV、英文词表、许可、元数据 | 默认仅本地；显式 `--fetch` 才下载缺失源并可登记缺失的锁记录 |
| `prepare_pronunciation_data.py` | 固定读音源＋已生成的简繁 TSV、五笔库 → 读音 SQLite、覆盖审计、许可和元数据 | 不联网；依赖前两步产物；输出审计到 `third_party/pronunciation/coverage.json` |
| `prepare_root_data.py` | 原始字根键位＋本地四键校正＋指定的原始字体 → 字体子集、字根 JSON、许可、元数据 | 仅需要重建字根字体时执行；要求 `fonttools==4.55.8` 与 `--source-font`，校验原字体哈希，不自动下载 |
| `verify_root_font.py` | 固定原始字体＋已生成字体 → 字形覆盖、现有轮廓/字宽、P 专用形态检查 | 重建后执行；同样要求 FontTools 和 `--source-font`，普通构建不依赖 FontTools |
| `verify_assets.py` | 锁记录、原始文件、全部产物、Manifest、迁移排除 XML | 只读检查，不连接设备、不联网；不证明最终 APK 或实际运行表现 |

重建字根字体的命令格式为 `python tools\prepare_root_data.py --source-font <原字体路径>`。原始字体并未作为完整字体随仓库保留，获取位置和哈希在锁文件中；普通构建使用已生成的子集，不需要 fonttools。

`third_party/wubi_roots/keys.json` 保留上游原始提取结果及锁定哈希；`tools/wubi_root_corrections.json` 单独记录 B/L/O/P 的完整修正列表、日期、原因及事实参考，生成脚本只覆盖这四键，并将校正记录打入 `wubi_roots.json`。原始 L/O 的 `{` 来自旧提取器将带排版命令的 TeX 参数首字符误当字根。P 的 U+E000 从固定原字体的礻移除末尾右点派生，使用独立字形和 cmap，不修改任何现有字符映射；历史字体声明保留并追加本次修改说明。其他键保持原列表，现有字形保持原轮廓和字宽。校验器检查四键必需形态、无花括号、校正来源一致及其他键未变。

主动升级上游数据时，明确修改目标 revision、源文件、锁记录及相关生成脚本，重新生成受影响产物并检查差异、数量、首选和许可。不要为常规构建删除锁文件。校验器中的固定首选或数量断言若需调整，要写明数据变化原因。

当前 `data_info.json` 记录：136,239 条五笔记录、3,781 个 emoji 序列、53,076 条简繁映射、46,717 个英文词、262 个字根形态；读音有 44,440 个单字与 80,371 个词组键（含 33,219 个繁体别名）。70,391 个五笔单字中 41,467 个有读音。后续数量以元数据和覆盖审计为准，不能把“字库收录”“读音覆盖”“设备有字形”视为同一件事。

## 隐私调用边界

[`EditorPolicy`](../../app/src/main/java/com/fanson/ime/ime/EditorPolicy.kt) 区分两个标志：`password` 覆盖文本、可见文本、网页及数字密码；`privateField=password || IME_FLAG_NO_PERSONALIZED_LEARNING`。密码框初始使用英文（数字密码显示数字页），关闭英文补全、隐藏候选/字根、阻止打开 emoji；语言列表仍允许用户主动选择其他语言，字段策略不是语言选择禁令。只有隐私学习标志而非密码的输入框不会因此禁用英文词表或 emoji 浏览，见 [实现限制](KNOWN_GAPS.md)。

剪贴板的职责分开实现：

- `ClipboardController` 每次确认本应用是默认 IME，只读取每项已有的 `.text`；不使用 URI 强转或下载内容。全部为文本时用换行拼接，输出 TEXT/EMPTY/NON_TEXT/UNAVAILABLE 快照，捕获 `SecurityException`。
- 服务在启动和复制回调调用 `captureClipboard()`；锁屏时跳过，在非 `privateField`、文本且非敏感时交给 store。监听属于服务生命周期，面板隐藏仍可记录；不能据此恢复安装前或未收到回调的历史。
- 面板对敏感或隐私会话内容默认遮挡，可由用户主动显示；更换快照时重置显示授权。粘贴当前值前重新读取，若有变化要求重新选择；历史粘贴使用选中的历史文字。
- 退出面板和失效会话释放快照与预览文字。emoji 成功提交且非 `privateField` 时才累加次数；五笔只有开启学习、非隐私、非内部搜索且平台接受的候选提交才增加衰减分数，密码/隐私五笔也不使用个人排序。其余普通输入及读音查询不保存正文历史；词频不保存逐次事件或整段正文。

Manifest 仅申请 `VIBRATE`；IME 服务导出并由 `BIND_INPUT_METHOD` 保护，启动 Activity 导出供桌面启用。`allowBackup=false` 同时配合 `backup_rules.xml`、`data_extraction_rules.xml` 排除云备份和设备迁移；数据位于无备份私有目录。无网络权限、无输入日志是维护约束，新增依赖、日志和外部组件时必须核对最终 APK。
