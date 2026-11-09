# 界面与面板

基线见 [入口](README.md)。当前界面通过原生 `LinearLayout/GridLayout/ScrollView/Button` 程序化创建。大部分布局在服务内构建，不采用 XML 键盘、Compose 或 RecyclerView。

## 渲染入口与状态归属

| 入口/组件 | 职责 |
| --- | --- |
| `render()` | 重建顶部和 body，关闭弹层、取消连删，保留 emoji 分类横向位置后重绘 |
| `renderCandidates()` | 根据输入状态切换候选/工具栏，提交候选快照，更新 Z 键状态 |
| `renderBody()` | 重建内容；有展开候选时优先渲染网格，否则依据 panel 渲染 |
| `Ui` / `Palette` | dp、布局、按钮、标点长按键、矢量图标、颜色及主题；提交动作由回调交给服务 |
| `CandidateStrip` / `CandidateRowLayout` | 实际 View 测量/布局与可 JVM 测试的宽度规划算法 |
| `SymbolPanel` / `SymbolCatalog` | 固定符号分类与网格；通过回调提交完整符号字符串 |
| `LanguagePopup` / `WubiRootCard` | 语言选择与字根查看弹层；只回调业务动作或展示数据 |

服务持有 `panel`（`keyboard/numbers/symbols/emoji/clipboard`）、`expanded`、语言、搜索词、emoji 分类/页码、剪贴板显示状态等。`expanded` 是覆盖当前 body 的显示标志，不是第六种 panel。光标控制直接替换 body，也没有独立 panel 值。

## 固定顶部行

顶部固定 44dp：地球始终占左侧，右侧在工具栏和 `CandidateStrip` 之间切换。候选显示条件完整表达为：

```kotlin
val inputPanel = panel == "keyboard" || (panel == "emoji" && emojiSearching)
val showCandidates = inputPanel && currentCode.isNotEmpty() && !password &&
    (chinese || (english && spelling))
```

有无匹配不改变候选区域是否显示：非空编码无候选时显示修正提示。宿主正文是否为空也不影响此条件。韩语、英文直输、密码及纯辅助面板显示工具栏；emoji 五笔搜索非空编码时例外显示候选。新增面板时要明确它是否属于 `inputPanel`，不能仅隐藏候选内容而保留空白行。

当前结构是根节点内 header＋body；header 内 globe＋toolbar＋candidateRow，二者通过 `GONE/VISIBLE` 互斥。`SmokeInstrumentation.checkCandidateArea()` 会验证结构、44dp 高度和相邻位置，结构重构需同步调整有意义的断言。

## 候选测量、选择与展开

普通模式先测量每个候选按钮自然宽度；编码提示最多 120dp，且不超过当前候选条宽度的 30%。剩余区域交给 `CandidateRowLayout.plan()`：

1. 所有候选和间距能放下时，不预留箭头空间。
2. 放不下时预留至多 44dp 的箭头与间距，按顺序放完整候选，不横向滚动。
3. 第一项本身过长时使用剩余槽位显示省略文本；仍可点选原候选。

候选按钮直接选择，只有独立箭头展开/收起。普通展开网格三列，单元高 52dp，使用同一 `candidateSnapshot`。刷新输入会清除 `expanded`；若重新测量已不溢出，则通过延迟回调自动收起。该回调用 `revision`、当前溢出值和 attached 状态拒绝旧布局通知，避免在 `onMeasure()` 中直接递归重绘。

读音模式复用 44dp 高度，按钮内部两行显示“候选文字＋12sp 读音”。候选行使用横向 ScrollView，保留全部读音；溢出仍显示展开箭头。展开网格改两列，格内允许横向滚动。普通模式的无横滑/省略规则不能直接复用于读音模式。

[`candidateButton()`](../../app/src/main/java/com/fanson/ime/keyboard/CandidateButton.kt) 仅组织显示和无障碍文字；实际选择交给服务重新验证。读音模式对显示文字做空白归一化，提交仍用原 `Candidate.text`。

## 面板切换与输入目标

`switchPanel(next)` 先 `resolveThen()`，成功后清理剪贴板快照、搜索词/搜索开关/分页及展开标志，再切换。重复点同一面板返回主键盘。五笔无候选会阻止切换，保留编码；英文/韩语先确认当前预编辑。打开设置与长按空格光标控制也先 resolve。

| 面板 | 输入路径与局部状态 |
| --- | --- |
| 主键盘 | 字母进入当前引擎，数字行调用 `punct()`；简繁只用于五笔，拼写只用于英文，韩语“한/漢”禁用 |
| 数字 | 数字/运算符/下划线通过 `punct()`；返回不改语言，布局独立于主键盘数字行 |
| 符号 | 每次创建默认“常用”；分类条保留实例，切类只替换固定顺序网格，完整字符串一次传给 `punct()` |
| emoji 浏览 | 根据类别/搜索/字形过滤后取页面；点击完整序列直接插入宿主 |
| emoji 搜索 | 仅五笔提供入口；`emojiSearching=true` 时复用键盘与引擎，普通提交写入 `emojiQuery`，而不是宿主正文 |
| 剪贴板 | 读取快照和本地历史；当前值粘贴前再读，历史值直接选取；通过 resolve 后提交宿主 |
| 光标控制 | 长按空格先确认输入，再显示方向键；发 DPAD down/up，不读取全文 |

### 空格标签与常用标点（0.3.1）

`spaceButton(showLanguage)` 显式区分显示场景：`renderKeyboard()` 传 `true`，显示“␣＋当前语言”；`renderSymbols()` 传 `false`，只显示“␣”，同时不附加语言 `stateDescription`。两处都保留“空格”无障碍名称并调用既有 `space()`，不因标签差异改变输入。emoji 五笔搜索复用主键盘时也显示语言；主键盘空格长按光标控制保持。

三种语言的逗号、句号均通过 `Ui.punctuationButton()` 创建：中文长按分别输入全角“！”“？”，英文/韩语长按分别输入半角 `!`、`?`，短按均输入原标点。键帽采用“长按符号在上、短按符号在下”，上行较小且使用淡色，短按主符号保持原字号。`contentDescription` 仍为原标点，`stateDescription` 说明长按输出；`OnLongClickListener` 返回 `true`，由 Android Button 消费长按并抑制松手后的短按，不使用自建定时器或重复提交逻辑。

短按和长按均通过服务的 `punct()` 进入当前引擎：有五笔候选先确认首选，无匹配保留编码并提示；英文和韩语先确认原预编辑再追加标点；搜索模式写入 `emojiQuery`，不直接写宿主正文。符号页网格中的符号仍直接选择，快捷键用于三种语言主键盘及复用该键盘的五笔搜索。

搜索时五笔使用简体候选，不覆盖服务原有简繁选择。空格、回车和选字形成搜索关键词；只有明确点击 emoji 才向宿主提交 emoji。`insertEmoji()` 先排队重置五笔引擎，再校验会话、提交 emoji，成功且非隐私时记次数；保留当前网格实例与顺序，避免手指下条目换位。

浏览页每页 30 项；搜索每页 12 项；常用只保留最多 30 项、固定一页。`EmojiRanking` 按使用次数、最近顺序、默认种子顺序和原始顺序排序，先过滤不支持字形再填满。未使用的肤色变体不参与默认补位，使用过后可以进入常用；分类和搜索仍可显示变体。

字形能力用 `TextPaint.hasGlyph()` 检测，结果放 `glyphCache`，服务生命周期内复用。分类横向位置在重绘前读取，`EmojiCategoryStrip` 在首次布局后恢复；回调还检查分类条实例身份，旧 View 的通知不能覆盖新位置。切面板或失效会话重置分类条位置。

## 剪贴板显示与按键事件

当前内容默认预览最多 1,500 个 UTF-16 单元，展开最多 100,000，粘贴仍使用全文。历史预览每项最多取 1,000 单元、显示三行，粘贴也是全文；这些显示截取不能复用于实际输入。内容变化重置 `clipboardRevealed/clipboardExpanded`；隐私与持久化规则见 [数据与隐私](DATA_AND_PRIVACY.md)。

退格长按在 420ms 后启动，之后每 85ms 重复。触摸观察器返回 `false`，保留 Button 正常点击和无障碍 `performClick()` 通道；松手、取消、重绘、会话失效均取消重复任务。不要让触摸观察器吞掉所有点击。

震动开启时使用 10ms 单次效果，并限制至少间隔 30ms；这是触感逻辑，不参与输入节流。

## 弹层与界面约束

语言顺序来自 `InputLanguage.entries`，固定 English、五笔86、한국어。打开、取消或选择当前项不确认原输入；选择其他项才 resolve。新输入、重绘和会话失效关闭列表，返回键优先关闭语言列表。

字根卡片仅在五笔、非密码且 `roots=true` 时响应 A～Y 长按。用专用字体显示字根 JSON 的全部形态，长按返回 `true`，不提交字母。两类 PopupWindow 都以可见屏幕范围限制位置，尽量显示在锚点上方，不抢占输入焦点；重构时要保留横竖屏及 RTL 边界处理。

按键 tag、`contentDescription`、`stateDescription` 同时用于可访问性和仪器测试定位。图标更换要同步语义；显示读音、简繁、英文开关和韩语 Shift 状态要与实际状态一致。新增按钮优先复用 `Ui` 和现有矢量图标方式，不在 UI 点击回调里另写一条宿主提交链路。
