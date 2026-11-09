# 文档导航

产品文档解释功能与交互，技术文档解释代码如何实现，版本验证记录保存当次验收证据。新增功能时需要同步维护对应技术章节，不能只在版本记录里追加说明。

## 技术文档

从 [技术文档入口](technical/README.md) 开始阅读。技术说明于 2026-11-09 按当前 0.3.2 源码更新，验证状态以当次记录为准。

| 文档 | 解决的问题 |
| --- | --- |
| [架构与运行时](technical/ARCHITECTURE.md) | 模块职责、依赖、初始化、线程归属、生命周期和实现决策 |
| [输入链路与引擎](technical/INPUT_PIPELINE.md) | 按键到上屏、三种引擎、会话隔离、回车、退格和光标处理 |
| [界面与面板](technical/UI_AND_PANELS.md) | 顶部行、候选测量、面板状态、emoji 搜索、弹层和事件入口 |
| [数据与隐私](technical/DATA_AND_PRIVACY.md) | 数据库结构、设置键、资源生成、缓存、剪贴板和持久化边界 |
| [开发与验证](technical/DEVELOPMENT.md) | 构建命令、测试分层、修改入口、排查顺序和交付记录 |
| [待改进事项](technical/KNOWN_GAPS.md) | 已确认的实现限制、测试缺口及后续优化的验收方向 |
| [技术方案模板](technical/PROPOSAL_TEMPLATE.md) | 新增功能或跨模块改动前，如何记录设计与验证计划 |
| [命名与兼容性](technical/NAMING.md) | FansonIME 的现行写法、应用身份与历史标识保留范围 |
| [项目开发约定](../AGENTS.md) | 修改前阅读文档、实施边界、文档同步与验证要求 |

## 产品、版本与资料

| 文档 | 用途 |
| --- | --- |
| [项目 README](../README.md) | 项目介绍、功能范围、快速构建与交付入口 |
| [产品设计与历次修订](DESIGN.md) | 需求、交互约定及早期设想；历史章节应结合后续修订阅读 |
| [版本记录](CHANGELOG.md) | 用户可见功能与修复的版本归属 |
| [验证记录索引](validation/README.md) | 所有版本的验证证据，统一存放在 `docs/validation/` |
| [0.3.2 验证记录](validation/VALIDATION-0.3.2.md) | 地球快捷切换与默认语言的构建、回归及设备状态 |
| [0.3.1 验证记录](validation/VALIDATION-0.3.1.md) | 已验收版本及 IDE 本地配置恢复记录 |
| [0.3.0 验证记录](validation/VALIDATION-0.3.0.md) | 韩语版本与命名整理的历史验证 |
| [初版验证记录](validation/VALIDATION.md) | 0.1.0 的历史证据；其他版本见 [验证记录索引](validation/README.md) |
| [第三方声明](THIRD_PARTY_NOTICES.md) | 数据、字体、作者、许可及转换说明 |
| [截图目录](screenshots) | 各版本的界面验收截图 |

代码行为以源码为准；技术文档出现偏差时，核对源码、测试和产品要求后一起修正。历史验证记录不代表本次重新运行，也不自动代表后续版本仍已通过。
