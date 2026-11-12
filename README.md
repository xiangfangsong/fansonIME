# 放松输入法 · FansonIME

2026-11-12 同版本补充英韩长按：开启数字行时，句号长按 `?`，逗号左侧功能键长按单个 `"`，关闭时保持原布局。用户已确认真机验收通过并授权提交；同时按要求在设置作者邮箱下增加 Gitee、GitHub 项目超链接。实际检查见 [英韩补记](docs/validation/VALIDATION-0.4.2.md#2026-11-12-英韩功能键引号补记) 和 [项目链接补记](docs/validation/VALIDATION-0.4.2.md#2026-11-12-项目链接与提交)；下方 2026-11-11 验收为上一轮状态。

个人使用的 Android 12+ 离线输入法，支持 English、五笔86和 한국어。当前开发版本为 **0.4.2 Debug / 21**，不申请网络权限；支持五笔候选离线查读音，拼音输入方案尚未实现。

0.4.2 已修复首次包 L 裁切（恢复原字母位置，灰色撇号在右上角），并增加三语数字行 PC 符号长按、按数字行开关切换长按标点，中文句号保留问号、中文 1/9/0 使用全角 ！/（/）、中文简/繁长按成对引号后光标居中。完整规则见 [长按维护](docs/technical/KEYBOARD_SHORTCUTS.md)。本版同时新增默认开启的英语句首自动大写，Shift 三状态支持临时取消，独立于自动拼写；常用无撇号缩写固定第一候选，原词保留第二，空格仍提交原词。英语 L 长按可补撇号。详细规则见 [英语维护说明](docs/technical/ENGLISH_INPUT.md)，当前验证与真机状态见 [0.4.2 记录](docs/validation/VALIDATION-0.4.2.md)；本版 144 项 JVM、43 项专项模拟器检查、资源及零问题 Lint 已通过，已覆盖安装真机，用户于 2026-11-11 确认最终验收通过并授权提交，未授权推送。

0.4.1 新增轻震动强度滑动条（0–100%，默认 50%，0% 不振动），保留默认关闭的轻震动开关；松手保存并预览，不在拖动途中反复写库。关于页增加可选择复制的作者邮箱 `cnxfs@foxmail.com`。本次验证和真机状态见 [0.4.1 记录](docs/validation/VALIDATION-0.4.1.md)，123 项单元测试、128 项模拟器检查、资源校验及零问题 Lint 已通过；已覆盖安装真机，用户于 2026-11-11 确认真机验收通过并授权提交，未授权推送。

0.4.0 新增仅五笔的本地衰减词频，默认开启；同层候选按近期习惯排序，关闭恢复固定顺序。14 天半衰期、16 分上限、0.25 失效、两万条容量；学习只在内存更新，键盘隐藏后后台批量保存，无定时任务。短语、emoji 次数、五笔词频分为三个独立清空按钮。实现和维护见 [词频说明](docs/technical/WUBI_FREQUENCY.md)，本次验证与长期体验观察状态见 [0.4.0 记录](docs/validation/VALIDATION-0.4.0.md)。本次 121 项单元测试、124 项模拟器检查、资源校验和零问题 Lint 通过；已提前告知并覆盖安装真机；用户已确认功能生效并授权提交，算法适配、长期流畅度和耗电仍待持续使用观察，未授权推送。

此前同版本修正（2026-11-10）：修复 L/O 卡片误显示 `{`，补齐 L 的田、力、車，O 的火，B 的耳及 P 的非成字根；其他键保持。版本名及 versionCode 仍为 0.3.3 / 18，用户已确认本次真机验收通过并授权提交，未授权推送。证据见 [字根修正补记](docs/validation/VALIDATION-0.3.3.md#2026-11-10-字根修正补记)。

0.3.3 原版：符号页输入后默认返回主键盘，设置“输入习惯 → 输入符号后返回键盘”可关闭；emoji 保持连续输入。2026-11-09 的原版通过 103 项单元测试、105 项模拟器检查及零问题 Lint，用户已确认当时的真机验收通过。历史结果见 [0.3.3 验证记录](docs/validation/VALIDATION-0.3.3.md)。

0.3.2：地球短按往返英语与当前非英语语言，长按选择语言；设置增加默认语言，初始五笔86。已通过 103 项单元测试、100 项模拟器检查及零问题 Lint，覆盖安装真机后用户已确认验收通过并授权提交。详见 [0.3.2 记录](docs/validation/VALIDATION-0.3.2.md)。

0.3.1 主键盘空格保留“␣＋模式”，符号页只显示“␣”；三种语言逗号、句号键的长按符号显示在上方，中文长按输出“！”“？”，英文/韩语长按输出 `!`、`?`，短按保持原标点。修订包已通过验证并覆盖安装真机，用户已确认验收通过并授权提交 Git。

## 文档入口

- [项目许可证](LICENSE)：本项目原创代码和文档采用 Apache-2.0；第三方内容遵循各自许可，详见下文“许可证”。
- [文档导航](docs/README.md)：产品、技术、版本验证与第三方资料的统一入口。
- [技术实现文档](docs/technical/README.md)：架构、输入链路、界面面板、数据与隐私、开发验证及待改进事项，按当前源码整理。
- [产品设计与历次修订](docs/DESIGN.md)：功能与交互要求；历史设想与现行实现的区别见技术文档。
- [项目开发约定](AGENTS.md)：修改前参考文档和源码，完成后同步文档与验证；[命名约定](docs/technical/NAMING.md) 说明英文名与兼容标识。

## 当前功能

- 五笔86内置码表：136,239 条记录、70,391 个不同单字，主要覆盖基本区及扩展 A、B 区，另含少量 C～E 区字；使用 Unicode，并非仅限 GBK。支持简码、词组、候选展开、补全提示。
- 顶部地球短按往返当前非英语语言与英语，英语无回切记录时使用默认语言（初始五笔86）；长按弹出固定顺序 English、五笔86、한국어的单选列表，当前项勾选，不按频率重排。设置“输入习惯”可修改默认语言，修改后从该语言开始，日常仍记住主动语言；默认选英语且无非英语记录时回退五笔。主键盘空格分别显示“␣ English”“␣ 五笔86”“␣ 한국어”，符号页只显示“␣”。邮箱、网址、密码和数字字段临时使用英文或数字页，不覆盖常用语言。
- 三种语言主键盘逗号、句号键保留短按；关闭顶部数字行时长按 !/?（中文全角），开启时长按冒号/问号。英韩逗号左侧功能键仅在数字行开启时长按单个直双引号；中文简/繁长按成对引号后光标居中，与数字行无关，短按仍切换简繁。顶部数字长按对应 PC 符号，中文 1/9/0 为全角 ！/（/）；数字及功能键角标独立显示于右上角，保持主字符原位置。小字号长按提示位于原标点上方，长按松手不额外输入原标点或触发功能键短按。
- 韩语使用韩国 PC 两式（두벌식）键位，支持 11,172 个现代音节、复合元音、复合收音、后续元音重新组字及当前音节按键退格；文字实时在输入框组合，不需要候选栏。Shift 一次上档输入 ㄲ、ㄸ、ㅃ、ㅆ、ㅉ、ㅒ、ㅖ后恢复；底行“한/漢”短按无操作，数字行开启时允许引号长按，关闭时禁用。本版不含韩语联想、自动纠错或转汉字。
- 五笔底行“简 / 繁”表示当前输出状态，默认简体，可实时刷新当前候选；繁体单字保留一简多繁的全部映射，词组优先消歧。
- 英语句首自动大写默认开启，Shift 三状态可取消当前词自动大写；常用无撇号缩写固定首选，原始输入第二，L 长按输入撇号。
- 英文提供 46,717 词离线前缀补全（每次最多 80 个），`app` 可选 `apple`；底行 Aa 勾/横线图标短按开关补全，密码框不能启用补全，数字行开启时仍可长按引号。点击候选替换当前词，空格提交原词并加空格，回车先确认当前词；不自动纠正拼写错误、不保存英文输入。
- 普通候选固定单行且不可横向滚动；中文和英文的可见候选都可直接点击上屏。只有一行放不下时出现向下箭头，点击箭头才展开；读音模式例外，允许左右滑动看完整读音，不用省略号截断多音字。
- 主键盘顶部固定一行：左侧地球常驻，右侧空闲显示功能按钮，输入五笔编码或英文补全词时显示候选并隐藏按钮；编码提示合并在候选行内，清码或确认后恢复按钮。韩语、英文关闭自动拼写和密码输入时持续显示按钮；辅助页面不显示候选，五笔 emoji 搜索有编码时可选字。
- 五笔可按本地衰减词频调整同层候选，默认开启；关闭保持固定顺序。保留自定义短语、精确匹配优先，简繁输出可分别学习；隐私字段和 emoji 搜索不学习。设置可分别清空短语、emoji 次数和五笔词频。
- 空格选词；“输入习惯”的“五笔回车键功能”可选“上屏五笔编码”（默认）、“清除五笔编码”或“上屏首个候选”，仅作用于五笔。韩语回车一次完成组字并换行或执行发送等动作；英文保留先确认当前补全词的规则。五笔首个候选无匹配时保留编码，四码唯一自动上屏、可选第五码顶字和纠错保持。
- 三种语言共用默认开启的顶部数字行（1～9、0），可在设置关闭。Shift 从五笔86进入英文大写，英文再按切换小写，地球可选回五笔86；数字、符号、编辑动作、长按空格打开光标控制。
- 数字页使用通用“返回”按钮，运算符列为 `+ - * /`；底行有独立 `_` 键，与 0 对齐。
- 开启“字母键显示字根提示”后，五笔字母键保留简要字根，长按显示该键完整字根卡片；关闭后不响应长按查字根。内置专用字形覆盖 25 键、262 个基本及变形字根，查看不提交文字；移除首行长按输入数字。
- 五笔 Z 键使用“侧脸读出 Z＋放大镜”矢量图标：无编码时输入待上屏的小写 z，回车按当前设置处理；有候选时切换五笔提示与读音。所有候选均显示读音，不依赖原来是否有五笔码提示。英文模式正常输入 Z/z。
- 读音模式在原 44dp 候选行内采用“汉字在上、带调拼音在下”，单字显示全部已收录读音；词组优先查整词读音，未知词组明确标注“逐字”参考，缺字显示“暂无读音”。内置 44,440 个单字、47,152 个词组及 33,219 个繁体词组查询别名，覆盖当前字库 41,467 个单字；未收录的生僻字不猜读音。
- 查读音只影响本次编码，继续补码或退格修正时保持；四码唯一和第五码自动上屏暂缓，主动选字仍正常。选字、提交、清码或换会话后自动恢复普通候选；不增加设置开关、长按或弹窗。
- 主工具栏采用本地矢量图标；收起小三角在设置右侧，删除键盘底部“切换输入法”和“收起”工具行，换行用回折箭头显示。系统输入法选择页仅注册“放松输入法”，不另列五笔86子类型。
- Unicode 16.0 emoji：分类、肤色变体直接选择、按设备字形能力过滤；五笔模式支持中文关键词搜索，英文和韩语模式隐藏搜索入口。浏览和输入 emoji 后保留原语言；移除收藏和长按弹层。
- Emoji 常用按本地使用次数排序，最多 30 项且只有一页；同次数按最近顺序排序，无记录时默认补位。当前网格不在点击后立即重排，重新进入常用时刷新。
- Emoji 分类栏可左右滑动但无滚动条；选择分类或翻页重绘后保持原来的水平位置。
- 符号页默认进入常用，提供中文、英文、箭头、数学、序号、单位、货币、括号、图形、希腊和制表等 12 类。英文含完整 32 个可打印键盘标点，多字符符号整项输入；所有分类和符号顺序固定。输入后默认返回原语言主键盘，可关闭“输入符号后返回键盘”以连续输入并保留分类；emoji 保持连续输入。
- 剪贴板当前内容及最近 10 项普通文本历史、重复置顶、粘贴、单项删除、一键清空；标记为敏感的内容不入历史。
- 浅色、深色、跟随系统、键高、震动、字根提示，手动短语添加和删除。
- 启用引导、输入法选择、左上角对齐的多行本地试打区和数据许可查看。

## 隐私边界

无网络权限、无广告或统计 SDK、无在线资源下载。不保存输入正文历史，输入与剪贴板不写日志。按照用户要求保存的最近 10 项剪贴板文本、emoji 累计次数与最近顺序，以及五笔衰减词频、手动短语和设置，均放在应用私有无备份目录。五笔仅保存必要的候选身份、衰减分数和更新时间，不保存逐次历史；试打框正文不保存，但开启词频时同样参与候选学习。Emoji 统计不含日期、正文或应用名，隐私输入会话不更新。云备份与设备迁移均配置排除规则，旧收藏数据在升级时移除。

作为系统默认输入法且服务运行时监听复制变化，包括键盘或面板隐藏时；锁屏及隐私输入会话不自动记录，标记为敏感的内容不入历史。历史只记录实际接收到的复制事件，不能找回安装前或系统限制下未捕获的记录。重复复制置顶，超过 10 项淘汰最旧项，重启保留；一键清空同时清除系统当前内容和本输入法历史，不影响其他应用自建历史。输入给目标应用后，由目标应用处理该内容。

## 在本机构建

已使用本机缓存可用的 AGP 8.7.3、Kotlin 2.0.0、Gradle 8.9；compileSdk / targetSdk 为 35，minSdk 为 31。需要 JDK 17，默认系统 Java 8 不能用于该项目。

本机 SDK 地址已经写入被忽略的 `local.properties`。使用其他电脑时配置自己的 `sdk.dir` 或 Android SDK 环境变量。

```powershell
# 自动选择可用的 JDK 17/21，检查资源、编译 APK、运行单元测试和 Lint
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build_debug.ps1

# 构建依赖已缓存后，可全程离线执行
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build_debug.ps1 -Offline
```

输出：

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 单元测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint 报告：`app/build/reports/lint-results-debug.html`
- 仪器测试 APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

首次获取构建依赖需要电脑联网；手机端安装后运行不联网。

## 离线数据

`resources.lock.json` 记录上游 commit、下载 URL 与 SHA-256。`third_party/` 保留原始数据、作者和许可，`tools/prepare_data.py` 负责转换为内置 SQLite 与 JSON。

```powershell
# 已有锁文件与原始数据时，不访问网络
python tools\prepare_data.py
python tools\prepare_completion_data.py
python tools\prepare_pronunciation_data.py
python tools\verify_assets.py
```

只有主动更新数据版本时才重新选择上游 revision。不要手工修改生成后的数据库或校验值来跳过完整性检查。第三方数据来源与修改说明见 [THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)。

## 模拟器测试

测试使用工程目录 `.local/avd/` 中独立创建的 Android 12 AVD，不复用个人手机或已有模拟器数据。测试脚本要求明确的 `emulator-*` 目标，并通过 `ro.kernel.qemu` 再次校验。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\start_test_emulator.ps1
# 等待 adb -s emulator-5580 shell getprop sys.boot_completed 返回 1 后：
powershell -NoProfile -ExecutionPolicy Bypass -File tools\test_emulator.ps1 -Serial emulator-5580
```

脚本会在该模拟器关闭 Wi-Fi 与移动数据、安装两个测试 APK、选择本输入法，并使用专用测试文本验证五笔、英文、emoji 和剪贴板。不要把此脚本用于真机；它会覆盖及清空模拟器当前剪贴板。

## 真机调试安排

用户要求：**进入真机调试前先告知**。已提前告知，并在用户明确授权后，将 0.1.0 Debug 安装到 HUAWEI NOH-AN00 / Android API 31，打开应用首页，等待用户启用并试打。没有通过 ADB 切换真机默认输入法、输入测试文本或读取剪贴板。

本项目继续使用 `.local/avd/fansonIME_API31.avd` 的独立测试模拟器；用户已明确要求保留，不删除。

0.1.1 已在再次提前告知后覆盖安装到上述真机，并打开新版首页，等待用户测试名称与剪贴板历史。

0.1.2 的交互调整详见 [设计文档](docs/DESIGN.md) 的修订表及 [VALIDATION-0.1.2.md](docs/validation/VALIDATION-0.1.2.md)。

真机阶段重点检查华为系统下的启用流程、聊天软件候选及回车行为、剪贴板访问和清空、密码框、导航栏、横屏、连续快速输入与系统回收后恢复。

## 版本与当前范围

见 [CHANGELOG.md](docs/CHANGELOG.md) 与 [0.4.0 验证记录](docs/validation/VALIDATION-0.4.0.md)，历版证据统一收录在 [验证记录目录](docs/validation/README.md)。目前尚未包含拼音输入、英文/韩语自动调频、韩语联想/纠错/转汉字、图片/GIF 表情包和词库导入导出。后续版本以真机反馈逐项推进。

已验收的 0.3.2 内部构建编号为 17，通过 103 项单元测试、100 项模拟器检查及零问题 Lint，已覆盖安装真机并通过用户验收。详见 [0.3.2 验证记录](docs/validation/VALIDATION-0.3.2.md)，归档为 `dist/FansonIME-0.3.2-debug.apk`。0.3.1 及更早版本的验证记录保留，历史产物另行保存。

界面截图：[五笔候选](docs/screenshots/02-wubi-candidates.png)、[emoji 搜索](docs/screenshots/03-emoji-search.png)、[剪贴板](docs/screenshots/04-clipboard.png)。

0.1.1 新增：[剪贴板历史界面](docs/screenshots/06-clipboard-history-0.1.1.png)。

0.1.2：[新版键盘](docs/screenshots/01-keyboard-0.1.2.png)、[系统输入法选择页](docs/screenshots/08-input-method-picker-0.1.2.png)。

0.1.3：[候选栏](docs/screenshots/02-candidates-0.1.3.png)、[展开候选](docs/screenshots/09-expanded-candidates-0.1.3.png)、[分类栏保留位置](docs/screenshots/10-emoji-category-position-0.1.3.png)。

0.1.4：[单页常用 emoji](docs/screenshots/11-emoji-common-0.1.4.png)、[常用符号](docs/screenshots/12-symbols-common-0.1.4.png)、[数学符号](docs/screenshots/13-symbols-math-0.1.4.png)。

0.1.5：[表情页隐藏候选区](docs/screenshots/14-emoji-no-candidates-0.1.5.png)、[剪贴板页隐藏候选区](docs/screenshots/15-clipboard-no-candidates-0.1.5.png)、[符号页隐藏候选区](docs/screenshots/16-symbols-no-candidates-0.1.5.png)。

0.1.6：[繁体歧义候选](docs/screenshots/17-traditional-variants-0.1.6.png)、[英文前缀补全](docs/screenshots/18-english-completion-0.1.6.png)、[Aa 自动拼写开关](docs/screenshots/19-english-spelling-key-0.1.6.png)。

0.1.8：[Q 键完整字根](docs/screenshots/20-wubi-root-card-q-0.1.8.png)、[N 键完整字根](docs/screenshots/21-wubi-root-card-n-0.1.8.png)、[数字行与反查图标](docs/screenshots/22-number-row-0.1.8.png)。

0.1.9：[数字页返回与符号布局](docs/screenshots/23-number-panel-0.1.9.png)。

0.1.9 顶部优化：[空闲功能按钮](docs/screenshots/24-idle-toolbar-0.1.9.png)、[五笔候选](docs/screenshots/25-wubi-candidates-0.1.9.png)、[英文补全候选](docs/screenshots/26-english-candidates-0.1.9.png)、[英文关闭补全](docs/screenshots/27-english-direct-0.1.9.png)。

0.2.0：[多音字与 Z 图标](docs/screenshots/28-polyphone-readings-0.2.0.png)、[词组读音](docs/screenshots/29-phrase-readings-0.2.0.png)、[展开读音网格](docs/screenshots/30-expanded-readings-0.2.0.png)。

0.3.0：[语言列表](docs/screenshots/31-language-selector-0.3.0.png)、[韩语组字](docs/screenshots/32-korean-composition-0.3.0.png)、[韩语输入](docs/screenshots/33-korean-word-0.3.0.png)、[输入期间切换](docs/screenshots/34-selector-during-composition-0.3.0.png)。

设计原稿见 [DESIGN.md](docs/DESIGN.md)。实现选择：设置以小型 SQLite 表代替设计草案的 DataStore，避免增加运行时依赖；所有用户数据仍保存在无备份私有目录。

## 许可证

除另有许可声明的第三方内容外，FansonIME 的原创代码和文档采用 [Apache License 2.0](LICENSE)，允许在遵守许可条件的前提下进行商业使用、修改与分发。

第三方代码、词库、字体、数据及其派生产物继续遵循各自许可证，不因项目主许可证而改为 Apache-2.0，包括 LGPL-3.0 的五笔码表、CC BY-SA 4.0 的英文词表及 ARPHIC PUBLIC LICENSE 的字根字体。来源、修改说明和许可文件见 [第三方声明](docs/THIRD_PARTY_NOTICES.md)；随 APK 分发的许可文件位于 `app/src/main/assets/licenses/`。
