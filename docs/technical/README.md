# 技术文档入口

这组文档记录 FansonIME 当前代码实现，供后续加功能、排查问题和重构时使用。当前基线为 **0.3.1 / versionCode 16**，更新日期 **2026-11-09**；在历史提交 `95919fa` 基础上随 0.3.1 的文档、命名与功能改动同步维护，准确提交标识以 Git 历史为准。命名变更见 [命名与兼容性约定](NAMING.md)，本版行为及验证见 [0.3.1 记录](../VALIDATION-0.3.1.md)。

后续开发遵循仓库根目录的 [AGENTS.md](../../AGENTS.md)。技术文档已按基线源码核对，但不承诺无遗漏；开始修改前必须核对相关源码和测试，发现偏差时同步修正文档。

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
| 候选条布局 | [`CandidateStrip.kt`](../../app/src/main/java/com/fanson/ime/keyboard/CandidateStrip.kt)、[`CandidateRowLayout.kt`](../../app/src/main/java/com/fanson/ime/keyboard/CandidateRowLayout.kt) |
| 设置页面和偏好写入 | [`MainActivity.kt`](../../app/src/main/java/com/fanson/ime/settings/MainActivity.kt) |

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

提交前检查文档中的类名、方法名、默认值、SQL 限制和命令仍与代码一致。功能变化同时更新产品文档与版本记录，测试结果写入对应 `VALIDATION-<版本>.md`，不要把计划执行的检查写成已通过。
