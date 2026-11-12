# 技术文档入口

2026-11-12 同版本补充英韩功能键引号：数字行开启时，句号长按 `?`、逗号左侧功能键长按单个 `"`，关闭时保持原布局。用户已确认真机验收通过并授权提交；另按要求添加设置页 Gitee/GitHub 超链接。实际检查见 [英韩补记](../validation/VALIDATION-0.4.2.md#2026-11-12-英韩功能键引号补记) 与 [链接补记](../validation/VALIDATION-0.4.2.md#2026-11-12-项目链接与提交)，下段验收状态属于 2026-11-11 的上一轮中文调整。

这组文档记录 FansonIME 当前代码实现。当前开发版本为 **0.4.2 / versionCode 21**，2026-11-11 新增英语句首大写和缩写候选，并修订 L 右上角提示、三语数字行/标点长按及中文成对引号，长按规则见 [维护说明](KEYBOARD_SHORTCUTS.md)，实施与维护见 [英语输入说明](ENGLISH_INPUT.md)，实际验证见 [0.4.2 记录](../validation/VALIDATION-0.4.2.md)。本版 144 项 JVM、43 项专项模拟器检查、资源及零问题 Lint 通过；已覆盖安装真机，用户于 2026-11-11 确认最终验收通过并授权提交，未授权推送。0.4.1 / `8b51b32` 已获用户真机验收；0.4.0 词频长期体验继续观察。词频完整说明见 [维护说明](WUBI_FREQUENCY.md)，命名边界见 [命名约定](NAMING.md)。

后续开发遵循仓库根目录的 [AGENTS.md](../../AGENTS.md)。技术文档已按基线源码核对，但不承诺无遗漏；开始修改前必须核对相关源码和测试，发现偏差时同步修正文档。

2026-11-10 增加 B/L/O/P 字根同版本修正，仍为 0.3.3 / 18；其他键保持。相关界面、资源生成与验证说明已同步，用户已确认本次真机验收通过并授权提交，未授权推送，状态见 [字根修正补记](../validation/VALIDATION-0.3.3.md#2026-11-10-字根修正补记)。本次验证与上段原版验收分别保留。

## 阅读顺序

1. [架构与运行时](ARCHITECTURE.md)：先了解依赖与线程边界。
2. [输入链路与引擎](INPUT_PIPELINE.md)：理解状态如何转为宿主编辑操作。
3. [界面与面板](UI_AND_PANELS.md)：理解显示条件与辅助输入目标。
4. [数据与隐私](DATA_AND_PRIVACY.md)：核对持久化、资源及隐私约束。
5. [开发与验证](DEVELOPMENT.md)：按变更范围选择检查，再查 [待改进事项](KNOWN_GAPS.md)。

## 最先定位的代码

源码根目录为 [`app/src/main/java/com/fanson/ime`](../../app/src/main/java/com/fanson/ime)。

| 问题 | 代码入口 |
| --- | --- |
| 输入框切换、上屏、按键路由、面板切换 | [`FansonInputMethodService.kt`](../../app/src/main/java/com/fanson/ime/ime/FansonInputMethodService.kt) |
| 密码、数字、邮箱、网址字段判断 | [`EditorPolicy.kt`](../../app/src/main/java/com/fanson/ime/ime/EditorPolicy.kt) |
| 引擎契约、五笔状态机、回车策略 | [`WubiEngine.kt`](../../app/src/main/java/com/fanson/ime/engine/WubiEngine.kt) |
| 英文前缀补全、韩语组字 | [`EnglishEngine.kt`](../../app/src/main/java/com/fanson/ime/engine/EnglishEngine.kt)、[`KoreanEngine.kt`](../../app/src/main/java/com/fanson/ime/engine/KoreanEngine.kt) |
| 用户数据和五笔 SQL | [`LocalStore.kt`](../../app/src/main/java/com/fanson/ime/data/LocalStore.kt)，其中同时声明了 `WubiRepository` |
| 数字行/标点长按、L 角标及引号光标 | [长按维护说明](KEYBOARD_SHORTCUTS.md)、`KeyboardShortcuts`、`CornerHintButton`、`PairedQuoteCursor` |
| 候选条布局 | [`CandidateStrip.kt`](../../app/src/main/java/com/fanson/ime/keyboard/CandidateStrip.kt)、[`CandidateRowLayout.kt`](../../app/src/main/java/com/fanson/ime/keyboard/CandidateRowLayout.kt) |
| 设置页面和偏好写入 | [`MainActivity.kt`](../../app/src/main/java/com/fanson/ime/settings/MainActivity.kt) |

新增词频开发优先读 [五笔本地词频维护说明](WUBI_FREQUENCY.md)：参数、来源键、成功反馈、隐藏保存、并发、容量与迁移的完整契约。

## 本文档的约定

- “上屏”指经 `InputConnection` 向宿主提交文字；“预编辑”指仍可被引擎修改的临时输入。五笔编码保存在输入法内部，英文和韩语的预编辑还显示在宿主 composing 区域。
- “会话”对应服务维护的 `session` 令牌，用于拒绝跨输入框或失效后的异步任务；它不是用户身份，也不落盘。
- “主线程”负责 Android View、宿主编辑操作和英文/韩语状态；“worker”是服务的单线程执行器，负责五笔与离线资源加载。
- 描述的是已实现行为；建议和未完成能力只放在待改进事项或状态明确的方案中。文档中的实现决策是基于源码整理的当前选择，不追溯虚构的历史讨论。

## 后续如何维护

| 变更类型 | 需要同步的技术文档 |
| --- | --- |
| 生命周期、线程、依赖方向、初始化 | 架构与运行时；必要时输入链路 |
| 引擎规则、提交方式、语言或编辑器适配 | 输入链路与引擎；开发与验证 |
| 顶部行、面板、弹层、候选布局 | 界面与面板；开发与验证 |
| 设置键、表结构、资源版本、持久化范围 | 数据与隐私；涉及兼容性时附迁移方案 |
| 修复已登记限制或新增测试 | 待改进事项；开发与验证 |

小改动直接更新相应章节。新增语言、引入依赖、改变提交协议、迁移数据库或跨模块重构，先按 [技术方案模板](PROPOSAL_TEMPLATE.md) 在 `docs/technical/proposals/` 新建方案；方案注明状态，实现后把稳定规则归入上述文档。该目录在首次有方案时创建。

提交前检查文档中的类名、方法名、默认值、SQL 限制和命令仍与代码一致。功能变化同时更新产品文档与版本记录，测试结果统一写入 `docs/validation/VALIDATION-<版本>.md` 并更新 [验证索引](../validation/README.md)，不要把计划执行的检查写成已通过。
