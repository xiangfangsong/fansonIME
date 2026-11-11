# 英语句首大写与缩写维护说明（0.4.2）

状态：英语规则与 L 同版本修订已实现；当前 144 JVM、43 项专项模拟器及零问题 Lint 通过，已覆盖安装最终修订包，用户于 2026-11-11 确认真机验收通过并授权提交，未授权推送。首次包的 L 双行裁切由用户发现，现改为保持主字母基线的右上角提示。实际结果见 [0.4.2 验证](../validation/VALIDATION-0.4.2.md)，方案取舍见 [实施方案](proposals/2026-11-11-english-capitalization.md)。

## 职责和数据边界

- `engine/EnglishCapitalization.kt`：纯 Kotlin 大小写状态，不依赖 Android 编辑器或 UI。
- `ime/EnglishEditorPosition.kt`：仅预测已请求并获 InputConnection 接受的光标/预编辑起点，以识别自身位置回调；不保存正文，不代替宿主实际落字确认或回滚协议。
- `ime/EditorPolicy.kt`：允许自动大写的字段策略。
- `ime/FansonInputMethodService.kt`：主线程读取平台句首模式、应用现有 commits/composing、协调生命周期及键帽/Shift 图标。
- `engine/EnglishContractions.kt`、`EnglishEngine.kt`：原创缩写规则与前缀候选合并，词内撇号预编辑。
- 设置 `english_auto_capitalize` 默认 true，非法布尔值回退 true；沿用无备份 preferences，不修改 schema 版本 2。英文拼写仍为现有会话状态，不因为新开关自动启用。

正文、候选、光标周边内容不写日志、不落盘，不学习英文词频，不联网。无新权限、依赖或外部资源；原创规则/矢量图标遵循根 LICENSE 的 Apache-2.0，原第三方词表及许可原样保留。

## 三种 Shift 状态

| 状态 | 图标 | 点击 | 字母输入后 |
| --- | --- | --- | --- |
| LOWER 小写 | 原空心箭头 | 进入手动持续大写 | 保持小写 |
| AUTO 句首自动大写 | 新实心箭头，无底线 | 取消本次自动大写 | 只第一个字母大写，随后 LOWER |
| CAPS 手动持续大写 | 原实心箭头＋底线 | 切回小写 | 持续大写，跨单词保持 |

取消状态独立于 AUTO 图标：取消后即使重新判断为句首，也不立刻重启。当前词退格至空仍保留取消；明确分隔/完成当前词后解除取消。再次点击小写 Shift 即进入 CAPS。人工切回小写也抑制当前词自动大写。真正光标/选区变更清除临时取消，但保留人工 CAPS；新输入会话/隐藏后重开及语言切换重置英语状态。五笔 Shift 仍先提交原始编码再进入英语 CAPS；韩语一次上档规则不变。

`contentDescription` 保持“切换大小写”供原有入口和无障碍定位；`stateDescription` 分别说明当前状态及点击作用。字母键帽与实际下一次输入一致；AUTO 消耗后立即更新键帽。

## 句首判断与宿主回调

仅英语、设置开启、非内部搜索、普通文本字段、当前没有英文 composing 词时请求 `getCursorCapsMode(TYPE_TEXT_FLAG_CAP_SENTENCES)`。不要求宿主显式声明 CAP_SENTENCES，因此普通聊天/试打框也能启用。密码（含可见/网页密码）、邮箱（含网页邮箱）、URI、数字/电话/日期、TYPE_NULL 和 FILTER 字段不自动大写，人工 Shift 仍可使用。`IME_FLAG_NO_PERSONALIZED_LEARNING` 不禁止这个无学习、无持久化的功能。

平台返回值需接受 CAP_WORDS 与 CAP_SENTENCES：Android TextUtils 在段首（包括空文本/换行）即使只请求 CAP_SENTENCES，也会返回 CAP_WORDS，不能只检查句子位。请求仍仅 CAP_SENTENCES，避免每个普通单词首字母都大写。

平台通常识别文本开头、换行、句末 `. ! ?` 与空白及引号/括号；逗号、普通词间空格、小数中间不直接成为句首。平台/宿主返回 0 或不可用时保守用小写；`Dr.`、网址形态、非英文标点等不做完整语法识别，不承诺所有宿主都相同。

只在开始/重开输入、进入英语、当前词完成或分隔、退格及真实位置改变时核对。composing 非空时不请求句首查询；普通字母沿用已确定状态，没有后台定时器、线程切换或逐键 SQL。

位置预测记录选区及 composing 起点，接受的 `commitText` 替换 composing/选区，`setComposingText` 复用原起点，finish 清理起点，字符簇删除按实际 UTF-16 长度移动预测。进入英语和重新显示输入视图时用 `getSurroundingText(0,0,0)` 对齐绝对位置。选择回调等于预测时视为自身编辑；不等时核对实时零字符位置，旧回调不清状态。真实新位置先 finish composing、清引擎临时词，再计算大写，防止新输入替换旧位置。该机制不读取全文，宿主拒绝/调整编辑的完整回滚仍为 G01 已知缺口。

维护时不能在每个 onUpdateSelection 无条件 reset：宿主自身编辑回调可能迟到、合并，容易把快速连打的新词或临时取消清掉。也不能在每个字母后重新自动大写。

## 缩写表与候选顺序

首版支持以下 32 个规范输出。快捷输入为移除 ASCII 撇号并转小写：

```text
I'm  I'd  I've  it's  you're  you've  you'll  you'd
he's  she's  that's  there's  what's  who's  let's
don't  doesn't  didn't  can't  couldn't  won't  wouldn't
shouldn't  isn't  aren't  wasn't  weren't  haven't
hasn't  hadn't  mustn't  needn't
```

例如 im→I'm、id→I'd、its→it's、youre→you're。缺撇号必须完整命中，不在 i/it/you 等短前缀注入缩写，也不对 imagine 等长词截断匹配。were→we're、well→we'll、ill→I'll、shell→she'll 暂不加入，避免大量常见单词被占首位。its、id 等已明确收录的歧义输入始终保留原词第二项。

候选顺序：精确快捷命中时“缩写、原始实际输入、普通前缀补全”；手动撇号前缀则优先匹配表中规范形式；无命中时保留原前缀规则。合并按最终 text 去重，总数不超过 80，普通词表原精确优先/频率排序不变。I 的缩写正常为 I'm/I'd/I've，即使取消句首大写也如此；原始 im/id/ive 仍是第二项。首字母大写输入显示 It's/You're，全大写输入显示 IT'S/YOU'RE。

点击候选才提交替换当前 composing；空格仍提交原词＋空格，标点仍原词＋标点，回车有词先确认原词、不附换行。故 its 按空格仍得到 its，而非自动改为 it's。切面板/语言及关闭拼写先 resolve 原词。关闭拼写时无候选，输入逐字提交；句首大写依然可独立开启。

英语 L 键保留右上角灰色提示的长按 ASCII `'`，短按仍输入 L/l，长按及松手不额外插入 l；L 主字母保持原单行位置；底部标点随顶部数字行开关切换，见 [长按维护说明](KEYBOARD_SHORTCUTS.md)。启用拼写时，已有 ASCII 字母词尾输入第一个 `'` 会保留在同一 composing 中，后续字母和退格继续编辑；空词/第二个撇号/64 字符边界则作为普通标点确认。符号面板原有“打开前先确认当前词”的行为保持。

## 性能及维护验证

缩写精确命中用固定 32 项内存哈希表；仅手动撇号前缀扫描这 32 项。无新增词频库、后台任务或写盘。英文旧词表前缀范围排序仍可能是主线程热点（G10），本次不借机改动其结构。句首查询是同步宿主接口，边界操作在复杂宿主上的耗时仍需真机观察，不能用 JVM 算法测试替代实际体验。

至少回归：三状态、取消后删空、下一句重新触发、大小写键帽、快速跨词/句、候选替换长度、选区替换与旧回调、重新打开、开关持久化与关闭拼写、密码/邮箱/URI/数字排除、I 规范形式、its 原词、手动撇号、80 项限制、五笔 Shift 和韩语一次上档。实际执行证据集中在版本验证记录。
