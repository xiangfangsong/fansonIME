# 开发与验证

0.5.1 的 `BackupFrequencyTest` 覆盖快照/恢复/代数；`PersonalBackupChecks` 使用合成 JSON 与独立 SQLite fixture 验证分类、格式、事务和界面。专项执行 `tools/test_emulator.ps1 -Serial emulator-5580 -Focus backup`；完整回归也包含备份检查。系统选择器真实打开与返回、测试拦截返回 URI 的区别，以及本轮实际命令见 [0.5.1](../validation/VALIDATION-0.5.1.md)。规格见 [个人备份](PERSONAL_BACKUP.md)。

0.5.0 新增 `PinyinEngineTest`（合成词典的拼音、分隔符、清码、无匹配、回车、简繁）与仪器测试 `checkPinyinUi`（实际离线数据、Z 真实触摸与队列屏障、生命周期、搜索、偏好、字根及主题）。可用 `tools/test_emulator.ps1 -Serial emulator-5580 -Focus pinyin` 专项验证；完整回归省略 Focus。资源脚本增加拼音数据库、压缩源原始哈希及许可检查，离线重建 `python tools/prepare_pinyin_data.py`；当次结果见 [0.5.0](../validation/VALIDATION-0.5.0.md)。

基线见 [入口](README.md)。本文描述可重复使用的开发流程；当前开发版本结果见 [0.5.0 验证记录](../validation/VALIDATION-0.5.0.md)，此前 0.4.2 见 [历史记录](../validation/VALIDATION-0.4.2.md)，已验收与历史结果见 [0.3.2 验证记录](../validation/VALIDATION-0.3.2.md)、[0.3.1 验证记录](../validation/VALIDATION-0.3.1.md)，不能据此声称某次新改动也已通过。

## 构建环境与命令

当前仓库配置：AGP 8.7.3、Kotlin 2.0.0、Gradle wrapper 8.9、Java/Kotlin 目标 17；`compileSdk/targetSdk=35`、`minSdk=31`。生产代码仅依赖 Android 平台，JVM 测试依赖 JUnit 4.13.2。release 开启压缩，规则入口为 `app/proguard-rules.pro`；Debug 验证不等于已验证 release。

Gradle 根工程名为 `FansonIME`，工作目录可继续为原小写路径；应用 ID 仍为 `com.fanson.ime`。新归档 APK 使用 `dist/FansonIME-<版本>-debug.apk`，旧归档与 AVD ID 保留历史名称，详见 [命名约定](NAMING.md)。

准备 Android SDK、JDK 与 Python 3，SDK 路径配置在本机 `local.properties` 的 `sdk.dir` 或环境中。`.local/`、`local.properties`、构建产物、dist 和签名密钥由 Git 忽略，不应作为共享配置提交。

在仓库根目录执行：

```powershell
# 自动选择 JAVA_HOME、常见 JDK 17 或 Android Studio JBR 中的 JDK 17/21
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build_debug.ps1

# 依赖已缓存时
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build_debug.ps1 -Offline
```

脚本先执行 `python tools/verify_assets.py`，再执行 `assembleDebug`、`testDebugUnitTest`、`lintDebug`、`assembleDebugAndroidTest`，失败即退出。它不会重新生成字典、安装设备、归档 dist 或启动模拟器。

已配置合适 `JAVA_HOME` 时，可按范围单独运行：

```powershell
python tools\verify_assets.py
.\gradlew.bat :app:testDebugUnitTest --offline --console=plain
.\gradlew.bat :app:lintDebug --offline --console=plain
```

| 输出 | 路径 |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 测试 APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| JVM 测试 HTML | `app/build/reports/tests/testDebugUnitTest/index.html` |
| JVM 测试 XML | `app/build/test-results/testDebugUnitTest/` |
| Lint HTML | `app/build/reports/lint-results-debug.html` |

## Android Studio 导入与改名后的本地配置

从仓库根目录打开工程，执行 Gradle Sync。IDE 的 Gradle JDK 与命令行 `JAVA_HOME` 是两处配置；构建脚本通过不代表 IDE 已使用正确 JDK。在 `Settings > Build, Execution, Deployment > Build Tools > Gradle` 为本项目选择 JDK 17 或 21。当前 Gradle 8.9 不使用 Studio 自带的 JBR 25；也不能沿用旧的 Java 13。配置机制见 [Android 官方 JDK 说明](https://developer.android.com/build/jdks)。

改 `rootProject.name` 后，已有 IDE 模块模型与运行配置可能仍保留旧前缀。2026-11-09 本机日志在导入阶段报 `Can't find module entity for fansonIME.app`，外部模块缓存仍含 `fansonIME.app`，运行配置还指向 `fansonIME.app.unitTest`；这发生在 IDE 项目结构导入阶段。

排查和恢复步骤：

1. 先执行 `gradlew.bat projects :app:assembleDebug --offline --console=plain`（使用 JDK 17/21），确认根工程为 `FansonIME`、包含 `:app`，区分 Gradle 构建错误与 IDE 导入错误。
2. 优先执行 Gradle Sync；若模块实体错误持续，保存文件并退出 Android Studio，避免它把内存中的旧配置写回。
3. 备份 `.idea/`、`.gradle/config.properties` 及本项目的 IDE 缓存，再重建导入配置。Studio 2026.2.2 在本机将模块保存于 `%LOCALAPPDATA%/Google/AndroidStudio2026.2.2/projects/<项目名与位置哈希>/external_build_system/`，项目模型保存于同目录 `project-model-cache/`。先核对缓存归属，再将这个项目专用缓存目录整体移到备份位置，让 IDE 重新生成；路径随 IDE 版本和项目位置变化，不得照抄项目哈希、清空所有项目缓存或改 SDK 全局配置。
4. 本地 `.idea/gradle.xml` 的 `gradleJvm` 应指向实际 JDK 17/21；使用 `#GRADLE_LOCAL_JAVA_HOME` 时，同时核对被 Git 忽略的 `.gradle/config.properties` 中 `java.home`。项目 SDK/语言级别也不应残留 Java 13。保留应用 ID、源码、个人数据、签名和测试 AVD。
5. 重新打开根目录并完成 Gradle Sync，以实际导入的应用模块重建 `Android App` 运行配置（本项目为 `FansonIME.app`），不要选择 `.unitTest` 或 `.androidTest` 源集。检查 Sync 成功以及运行配置无模块错误；仅命令行构建成功不能作为 IDE 修复成功的证据。

本地 `.idea/`、`.gradle/config.properties` 和 IDE 缓存均不提交。工程改名的验收应同时包含命令行与 IDE 导入、运行配置核对，不能仅搜索源码中的品牌大小写。

## 测试分层与源码入口

当前有 15 个 JVM 测试文件、153 个 `@Test`；这是源码清点，执行状态以实际报告为准。

| 层次/文件 | 验证内容 | 不能代替的验证 |
| --- | --- | --- |
| [`WubiEngineTest`](../../app/src/test/java/com/fanson/ime/engine/WubiEngineTest.kt) | 简码、四码/第五码、无匹配、回车策略、词组取码、reset | Android SQL、真实宿主时序 |
| [`EnglishInputTest`](../../app/src/test/java/com/fanson/ime/engine/EnglishInputTest.kt) | 英语三状态、取消、缩写优先/原词、大小写、词内撇号与候选限额 | 实际宿主句首判断与触摸 |
| [`EnglishEditorPositionTest`](../../app/src/test/java/com/fanson/ime/ime/EnglishEditorPositionTest.kt) | 预编辑锚点、候选/选区替换、删除、未知位置 | 宿主接受/调整编辑后的实际位置 |
| [`CompletionTest`](../../app/src/test/java/com/fanson/ime/engine/CompletionTest.kt) | 简繁多义、实时转换、英文大小写/补全/原词提交 | composing 的真实替换效果 |
| [`PronunciationTest`](../../app/src/test/java/com/fanson/ime/engine/PronunciationTest.kt) | 多音字、词组/逐字、扩展码点、Z 状态与自动提交保护 | 字典真实覆盖和长读音 UI |
| [`KoreanEngineTest`](../../app/src/test/java/com/fanson/ime/engine/KoreanEngineTest.kt) | 全 11,172 现代音节与 NFC 对照、复合收音拆分、Shift、逐键回退 | Android 连续输入和光标回调 |
| [`EditorPolicyTest`](../../app/src/test/java/com/fanson/ime/ime/EditorPolicyTest.kt) | 密码/数字识别、语言临时覆盖 | 当前未单测 `privateField()` 的 NO_PERSONALIZED_LEARNING 分支 |
| [`CandidateRowLayoutTest`](../../app/src/test/java/com/fanson/ime/keyboard/CandidateRowLayoutTest.kt) | 宽度、箭头、长首项、读音宽度规划 | 实际字体测量、触摸和横滑 |
| [`SymbolCatalogTest`](../../app/src/test/java/com/fanson/ime/keyboard/SymbolCatalogTest.kt) | 分类顺序、符号覆盖、多字符完整性 | 实际面板布局 |
| [`EmojiRankingTest`](../../app/src/test/java/com/fanson/ime/emoji/EmojiRankingTest.kt) | 次数/最近顺序、补位、支持字形过滤、30 项限制 | 实际字形和完整删除 |
| [`WubiFrequencyTest`](../../app/src/test/java/com/fanson/ime/engine/WubiFrequencyTest.kt) | 衰减、分层、来源、容量、并发/失败快照、提交反馈 | 真机长期排序体验 |
| [`HapticFeedbackTest`](../../app/src/test/java/com/fanson/ime/keyboard/HapticFeedbackTest.kt) | 0% 静音、偏好边界、两类硬件下所有百分比的合法与单调映射 | 实际硬件振感 |
| [`verify_assets.py`](../../tools/verify_assets.py) | 源/产物哈希、SQLite 完整性和首选、读音样例、词表/字根/emoji、源 Manifest 权限及迁移排除 | 合并 Manifest、最终 APK、动态隐私行为 |
| [`SmokeInstrumentation`](../../app/src/androidTest/java/com/fanson/ime/SmokeInstrumentation.kt) | Android 仓库和持久化、View 测量、软键盘操作、真实 composing、面板、剪贴板及截图 | 真机/不同宿主应用/性能分布 |

仪器测试使用自定义 `android.app.Instrumentation` runner，不是 AndroidJUnitRunner。测试通过 `passed` 标记积累结果；失败返回 `failure` 和测试 UI/IME 状态摘要。`await()` 轮询实际条件（每 50ms、最多 8 秒），新增异步检查优先等待最终可观测状态，不依赖固定睡眠来猜测五笔结果何时到达。

截图生成到模拟器中应用 `externalCacheDir/qa/`，不是自动写入仓库的 `docs/screenshots/`；需要导出并检查后再归档。0.3.0 原记录有 87 项检查，这不是 87 个 JUnit 方法。

0.3.1 增加六组仪器检查：三种语言主键盘/符号页的空格标签和输入；三种语言上行长按提示、短按及真实按下/保持/松手；英文与韩语先确认预编辑再输入半角标点；有候选确认/无匹配保护；emoji 搜索目标隔离；关于页版本与实际安装包一致。长按验证使用系统长按阈值并实际发 DOWN/UP，不能只用 `performLongClick()` 证明松手不会额外输入原标点。

面板切换测试先等待目标面板的特有控件出现，再核对候选区、空格与布局。`checkCandidateArea(false)` 同时适用于空闲主键盘和辅助面板，不能单独证明异步切换已经完成；布局相关检查等待实际测量结果，不把创建 View 等同于已完成布局。

真实触摸还要等待按键完成布局且屏幕坐标/尺寸稳定；从不同高度面板返回时，View 已完成布局也可能仍处于 IME 窗口位置变化中。一般菜单逻辑用 `performLongClick()` 触发监听器，另以专用真实 DOWN/UP 用例验证短按、长按阈值及松手抑制，避免把坐标时序失败误判成语言逻辑失败。

0.3.2 在上述检查基础上增加默认语言/旧设置兼容与回切目标持久化、英语起始回退、三语短按往返、无匹配五笔不更新偏好、长按地球真实触摸抑制短按、设置三项保存与生效、Shift 回切。菜单测试统一用地球长按打开，短按用例独立验证不会弹出菜单；执行结果见 [0.3.2 验证记录](../validation/VALIDATION-0.3.2.md)。

0.3.3 增加五组符号自动返回检查：设置默认开启；三语言真实触摸完整符号后返回原语言；分类/空格/退格/回车不返回；关闭开关持久化及三语言连续输入保留分类；两种开关状态下 emoji 均可连续输入，重新开启后符号恢复返回。原符号分类连续输入回归显式关闭此开关，结束后恢复开启。

2026-11-10 同版本字根修正增加一组仪器检查：逐一打开 L/O/P/B 卡片，核对完整列表、实际 Typeface 字形覆盖及 P 非成字根的无障碍名称；截图供人工检查笔形，同时确认原编码和宿主正文不变。资源检查比较原始表，保证其他 21 键不变。重建字体后另执行 `python tools/verify_root_font.py --source-font <原字体路径>`，核对所有现有轮廓/字宽及新 P 形态；普通构建仍不依赖 FontTools。当前同版本修正的验收状态独立记录在 [0.3.3 补记](../validation/VALIDATION-0.3.3.md#2026-11-10-字根修正补记)，不能沿用此前真机验收。

## 0.4.0 五笔词频检查

新增 WubiFrequencyTest 覆盖纯衰减/分层/容量/快照/代数及引擎提交元数据；WubiFrequencyChecks 使用 cacheDir 下明确命名的合成 version 1 fixture 验证增量迁移与旧数据保留，完成后仅删除该 fixture。数据检查覆盖批保存、补回来源、独立清空及同名内置兼容。

SmokeInstrumentation 的 checkFrequencyUi 覆盖真实默认开关、后排提升、关闭固定回退、不学习、重新开启、真实收起保存、NO_PERSONALIZED_LEARNING、emoji 搜索和三个清空确认。原固定顺序回归在此后显式关闭学习，最终恢复开启，避免旧首选断言被合成学习污染。

合成性能输出到 externalCacheDir/qa/frequency-performance-0.4.0.json，包含近似堆差、模型排序/补回/更新分位数、仓库固定查询和学习引擎查询，以及首次/单行事务耗时。近似堆值受 GC 和同时运行的应用影响，Debug API31 模拟器数据不能替代真机、端到端或电量测试。详细执行与失败修正均写入 [0.4.0 验证](../validation/VALIDATION-0.4.0.md)。

## 0.4.1 触感与邮箱检查

HapticFeedbackTest 遍历 1–100% 验证振幅/时长参数合法与单调，确保 0% 不向单次效果传非法振幅，覆盖缺失/非法/超界偏好。SmokeInstrumentation.checkHapticSettings 检查旧安装默认 50%、开关门控、真实 DOWN/MOVE/UP 拖动期间不写库/松手保存、无障碍 0/100% 调整、设置页面重建和新连接保持，以及关于邮箱精确文案可选择。新检查结束恢复震动关闭、强度 50%、主题跟随系统，不污染后续回归。模拟器无法证明实际马达强弱，真机需用户确认。

## 模拟器回归

项目复用 `.local/avd/fansonIME_API31.avd`，用户要求保留该测试 AVD，不删除、不重建已有数据目录。启动脚本要求 Android 31 Google Play x86_64 系统镜像，可显式传 `-Sdk`；默认端口 5580。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\start_test_emulator.ps1
adb -s emulator-5580 shell getprop sys.boot_completed
# 上一步返回 1，且已构建两个 APK 后：
powershell -NoProfile -ExecutionPolicy Bypass -File tools\test_emulator.ps1 -Serial emulator-5580
```

回归脚本只接受 `emulator-*` 且二次校验 `ro.kernel.qemu=1`。它会关闭目标模拟器 Wi-Fi/移动数据、覆盖安装两个 APK、启用并设为默认输入法、执行仪器测试；测试会更改设置、短语、emoji 次数和合成剪贴板。不得用于真机或含重要数据的模拟器。脚本以 ADB 退出码、`INSTRUMENTATION_CODE: -1` 且无 `failure=` 判定成功。

启动脚本只在本项目 AVD 使用无窗口、软件 GPU 和禁用 Vulkan/WiFiPacketStream/Uwb 等参数，原因见原验证记录；不修改用户其他 AVD 的全局配置。

真机安装或调试前按项目既有约定提前告知用户；不要把模拟器脚本直接改成真机脚本。需要覆盖安装时核对目标设备、版本与签名，保留用户数据。日常应用的发送、剪贴板清除等操作应与用户确认的调试范围一致。

## 按改动定位影响范围

| 要修改的能力 | 实现入口 | 至少考虑的回归 |
| --- | --- | --- |
| 五笔排序/自动提交/回车 | `WubiEngine`、`WubiRepository`、`enter()` | 精确/补全、无匹配、简繁歧义、读音、面板切换 |
| 英文补全/预编辑 | `EnglishEngine`、`enqueue()`、`onUpdateSelection()` | 快速跨单词、选候选替换、开关、密码、光标/选区、会话切换 |
| 韩语规则/键位 | `KoreanEngine`、键盘渲染、`enter()` | 全音节和收音拆分、批编辑、Shift、逐键退格、一次回车/发送 |
| 新语言 | `InputLanguage`、`EditorPolicy`、服务引擎路由/键位/回车/光标分支 | 固定菜单、偏好兼容、字段覆盖、辅助面板、所有旧语言 |
| 候选界面 | `CandidateStrip`、布局规划、按钮、展开网格 | 无候选、刚好放下/溢出、长首项、旋转/宽度变化、读音完整横滑、旧布局回调 |
| 新面板/操作按钮 | `switchPanel()`、`renderBody()`、提交回调 | 未完成输入先 resolve、无匹配保护、候选显示、搜索目标、返回语言、弹层清理 |
| 新设置/个人表 | `MainActivity`、`LocalStore`、`readSettings()` | 默认值、未知值、旧库升级、重启保持、清除范围、无备份 |
| 剪贴板/emoji | Controller、Repository/Ranking、服务面板与提交 | 隐私/敏感/锁屏、失败、去重/次数、变体、字形、完整字符簇删除 |

小型文案和纯文档调整按范围做静态核对、链接检查即可。行为变更先跑对应单元检查；涉及 Android 交互再做构建、Lint 和模拟器回归。修改资源、权限或数据时跑资源校验。验证通过后，不因流程形式重复运行无新增价值的检查。

## 排查顺序

| 现象 | 先核对 |
| --- | --- |
| 无法构建 | JDK 是否 17/21、SDK 35、wrapper、离线依赖是否已缓存；先看首个资源/编译错误 |
| Studio 报 `Can't find module entity for fansonIME.app` | 按上文导入恢复步骤检查本项目旧模块缓存、运行配置和 Gradle JDK；不要为消除 IDE 错误改应用 ID 或源码包名 |
| 一直提示加载或加载失败 | `onCreate()` 的资源链、元数据哈希、字典私有副本；当前统一错误提示不区分资源，勿通过记录用户输入定位 |
| 五笔候选/选词不符合预期 | 输入编码与 `exact`、手动短语优先级、SQL 权重、简繁及读音开关；区分数据库结果与 UI 只显示一部分 |
| 英文/韩语重复、缺字或覆盖旧位置 | 引擎 `commits/composing`、同批编辑、真实/过期光标回调、session 与线程归属 |
| 面板或语言切换无响应 | 当前是否无匹配五笔码，导致 `resolveThen()` 未执行后续；数据是否 ready |
| 仪器测试等待窗口焦点超时 | 读取隔离模拟器 `dumpsys window` 的 `mCurrentFocus` 并检查失败截图；0.3.1 回归曾被系统蓝牙崩溃弹窗中断，关闭该模拟器蓝牙并清除弹窗后完整重跑，记录环境处理，不操作真机 |
| 剪贴板无记录 | 是否默认 IME、服务是否运行、锁屏/隐私/敏感、文本类型和回调是否实际送达 |
| emoji 或扩展字显示方框 | 先分清数据收录与系统字形支持；读音覆盖另查 coverage 审计 |

排查可记录非敏感状态、错误类型和耗时，禁止把编码、候选、正文、密码和剪贴板原文加入日志。仪器测试失败摘要仅应在隔离测试环境与合成数据下使用。

## 交付前记录

功能版本更新 `app/build.gradle.kts` 与产品/版本文档；技术行为同步相应章节。验证记录统一放在 `docs/validation/` 并维护目录索引，写清日期、版本、实际命令、结果、测试环境、未验证项及最终 APK 路径/哈希。权限和签名检查针对最终 APK；历史记录不能替代本次产物检查。Git 提交、推送和真机操作按当前用户指示执行，文档整理本身不隐含这些外部动作。

## 0.4.2 英语检查

EnglishInputTest、EnglishEditorPositionTest 和扩展 EditorPolicyTest 验证纯规则；SmokeInstrumentation 的 checkEnglishInput 验证实际默认/持久化、首字母/取消/持续大写、快打、句末引号/换行/小数、光标/选区、关闭拼写、缩写候选替换和原词、L 真实长按、密码/邮箱/URI。新功能检查后显式关闭句首大写，再执行原历史小写断言；整套结束恢复默认 true。所有输入为合成数据，仅运行指定隔离模拟器。

新增设置会改变页面高度；旧轻震动真实拖动用例请求滑动条下方额外留白后再确认屏幕位置，防止触点落入系统导航区域。实际失败/修正和最终结果见 [0.4.2 验证](../validation/VALIDATION-0.4.2.md)。

## 0.4.2 同版本长按修订验证

KeyboardShortcutsTest 当前 4 项（初次修订为 3 项）、PairedQuoteCursorTest 2 项，验证 PC 十键、开关/语言映射和引号光标边界；仪器新增七组 checkKeyboardShortcuts，覆盖 40dp/1.3 倍字体/深浅主题完整 L、三语数字及真实长按松手、预编辑确认、开关双态、引号各类插入位置与搜索隔离。旧 !/? 回归显式关闭数字行，并于整套末尾恢复默认 true。

`tools/test_emulator.ps1 -Serial emulator-5580 -Focus keyboard_shortcuts` 只运行基础准备与专项检查，不能作完整回归证据；不传 Focus 为原完整回归，原模拟器限制不变。详细命令及结果见 [本版验证](../validation/VALIDATION-0.4.2.md)，交互和布局维护见 [长按说明](KEYBOARD_SHORTCUTS.md)。

### 中文标点与简/繁键再调整

用户确认其他功能通过后，补充中文数字 1/9/0 全角、句号长按问号、简/繁角标长按引号。KeyboardShortcutsTest 新增中文例外映射测试，当前 14 个 JVM 文件、144 项测试。checkKeyboardShortcuts 增加第八组：在数字行开/关和简/繁两态下真实长按不触发短按切换，短按仍切换且不改正文/光标；布局对比扩展到简/繁，数字 native 长按扩展 1/9/0。当前专项测试不应冒充重新执行过原完整 145 项，实际执行见版本验证。

### 英韩功能键引号补充（2026-11-12）

句号映射去掉数字行参数，单测断言对应语言问号。checkKeyboardShortcuts 增加第九组：数字行开/关下的英韩功能键提示、真实长按松手与预编辑确认；英文拼写开/关时长按不切换、短按仍切换；韩语短按仍无操作、关闭数字行后禁用；密码框只在数字行开启时开放长按引号，短按不能启用拼写。原三语标点检查同步核对两种状态句号均为问号。实际命令、结果及未验证项见 [本轮补记](../validation/VALIDATION-0.4.2.md#2026-11-12-英韩功能键引号补记)。

### 设置页项目链接（2026-11-12）

两条固定地址复用平台 Linkify，不新增机械重复的单元测试。按局部界面范围构建、Lint，并在隔离模拟器检查完整地址/顺序、实际点击进入浏览器、返回设置页及布局截图；首次浏览器欢迎页和联网网页加载的验证范围分别记录。实际检查见 [链接补记](../validation/VALIDATION-0.4.2.md#2026-11-12-项目链接与提交)。
