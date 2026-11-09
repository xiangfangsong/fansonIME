# 0.3.0 验证记录

日期：2026-11-08。应用版本 0.3.0，内部构建编号 versionCode 14，Android 12+。按用户确认的方案加入韩语 PC 两式输入，待用户真机验收，暂不提交 Git。

## 本版行为

- 地球常驻 44dp 顶部行左侧，弹出固定顺序 English、五笔86、한국어；当前项勾选，不重排。右侧继续按输入状态显示功能按钮或候选，输入编码时地球仍可用。
- 空格标注“␣＋模式”；韩语底行“한/漢”为禁用占位，本版不含韩语联想、纠错或转汉字。
- 韩语使用 PC 两式键位和 Shift 一次上档，实时在宿主输入框组合文字，不显示候选；支持现代音节、复合元音/收音、后续元音拆分和当前音节逐键退格。
- 韩语回车一次完成组合并换行或执行发送/搜索等动作；不受“五笔回车键功能”三选项影响。五笔与英文原有规则保持。
- 主动选择语言后重开键盘保持；密码、邮箱、网址和数字字段临时覆盖，不自动修改常用语言。切换前确认原输入，无匹配五笔码不丢弃。
- 英文与韩语 emoji 面板不显示搜索功能，浏览、输入与返回保持语言。三种语言共用数字行、符号、剪贴板及键盘外观设置。

## 单元测试与编译

- 最终离线 Debug、androidTest 编译完成，103 项单元测试通过，0 失败；Lint：No issues found。
- 韩语测试通过全部 11,172 个现代音节，将物理按键序列产生的结果与 Java Unicode NFC 规范化独立核对；包括全部 11 组复合收音的后续元音拆分、7 个 Shift 字母、当前按键回退、单独字母及非法键。
- 字段策略验证三种常用语言与密码、邮箱、网址、数字字段的临时默认语言。
- 原五笔码表、简繁、英文、读音及其他离线资源均与 0.2.0 保持一致；资源完整性检查通过，无新增网络权限。`aapt dump permissions` 仅显示震动权限，`apksigner verify --verbose` 通过 v2 签名校验。
- 重新打包去除增量 ZIP 遗留空隙，归档 APK 与模拟器验证 APK 的全部解压条目逐字节一致。

## 模拟器与真机

沿用项目原有 AVD 和 emulator-5580，关闭 Wi-Fi 和移动数据；87 项检查全部通过，脚本退出码 0，`INSTRUMENTATION_CODE: -1`，无 failure 字段。仅在模拟器输入合成文字、操作测试剪贴板。

- 新增 13 组交互检查：固定语言顺序、当前项勾选及布局；实时组字和逐键退格；词组与收音重新组合；Shift 一次上档和复合元音；快速连续输入；三种五笔回车设置下韩语一次换行；韩语一次发送；相同语言保持组合和跨语言确认；候选期间切换和无匹配保护；光标与数字页；返回键取消列表、选择替换、剪贴板粘贴和符号页；英文/韩语无 emoji 搜索；记住语言与密码字段临时覆盖。
- 其余 74 项原有检查通过，覆盖五笔及 Z 查读音、简繁、英文补全、数字行、字根卡片、emoji、符号、剪贴板和生命周期。
- 实际输入框验证 `한글`、`안녕하세요`、`가나`、`달가`、`갑사`、`까`、`과`。检查宿主 composing 区域，退格及语言切换不重复上屏、不覆盖旧光标位置；音节边界提交与下一音节组合在同一批编辑内完成。
- 已人工检查并归档：[语言列表](../screenshots/31-language-selector-0.3.0.png)、[实时韩语组字](../screenshots/32-korean-composition-0.3.0.png)、[完整韩语输入](../screenshots/33-korean-word-0.3.0.png)、[候选期间切换](../screenshots/34-selector-during-composition-0.3.0.png)。

## 安装包与真机

- APK：`dist/fansonIME-0.3.0-debug.apk`，10,151,295 字节。
- SHA-256：`de86e9a41920430306be1514a2ce957e39fbb03f4c6eb06de1a510c4c005b815`。
- 已提前告知后覆盖安装 HUAWEI NOH-AN00，返回 Success；核对 versionCode=14、versionName=0.3.0，并打开设置首页。
- 保留原有设置及个人数据，未在真机输入测试正文、读写剪贴板或通过 ADB 切换默认输入法。
- 待用户真机验收，尚未提交或推送本版代码。

## 资料参考

- [Unicode 韩文组合算法](https://www.unicode.org/versions/Unicode18.0.0/core-spec/chapter-3/)：现代音节组合与分解；仅参考标准，运行时不依赖在线资料。
- [Microsoft 韩语输入说明](https://learn.microsoft.com/en-us/globalization/input/korean-ime)：PC 两式及 Shift 键位；本项目自行实现组字代码。

## 2026-11-09 文档与命名整理补记

本节记录统一英文名为 **FansonIME** 和补齐技术文档后的检查，应用仍为 0.3.0 / versionCode 14。以上 2026-11-08 的 APK 路径、哈希、87 项模拟器及真机记录保留原意，不表示本次重新运行。更名范围与保留标识见 [命名约定](../technical/NAMING.md)。

本次已执行：

- `powershell -NoProfile -ExecutionPolicy Bypass -File tools\build_debug.ps1 -Offline`：退出码 0，`BUILD SUCCESSFUL`；资源校验、Debug APK 和 androidTest APK 编译成功。
- JVM XML 报告：8 个测试套件，共 103 项，0 失败、0 错误、0 跳过；Lint XML 报告 0 项问题。
- 静态核对：技术文档及开发约定的本地链接、UTF-8、代码围栏和空白检查通过；Python 工具及模拟器启动脚本语法检查通过；`git diff --check` 通过。
- 与源码基线比较：应用构建身份配置、Manifest、输入法/备份 XML、中文名称资源、输入引擎、数据实现、锁记录及资源元数据未变；资源哈希校验通过。
- 最终 APK 经 `aapt` 核对应用 ID 为 `com.fanson.ime`，版本仍为 14 / 0.3.0，中文名称为“放松输入法”，Activity 与 IME 组件未改，仍仅申请 `VIBRATE`；新英文名已包含在 APK 代码与项目自写许可说明中。
- `apksigner verify --verbose --print-certs` 通过 v2 签名校验，签名证书 SHA-256 与原 `dist/fansonIME-0.3.0-debug.apk` 一致。本次未覆盖原归档、未安装设备。

本次生成 APK：`app/build/outputs/apk/debug/app-debug.apk`，10,185,228 字节；SHA-256：`f3c9a0744d5d9bc562d43762d84bb85898d397b20745ce65cbccddcd5a1f5cf6`。今后新归档按 `FansonIME-<版本>-debug.apk` 命名。

未执行模拟器/真机交互、实际覆盖升级、关于页视觉验收和 release 构建。本次没有修改输入逻辑，身份/数据配置与签名核对支持升级兼容性判断，但不替代设备验证。构建出现 SDK XML 版本提示与现有状态栏 API 弃用警告，未阻断构建，未在命名调整中扩展处理。
