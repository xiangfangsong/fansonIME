# 0.3.3 验证记录

原版日期：2026-11-09；版本：0.3.3 / versionCode 18。原版状态：电脑端及默认值升级场景验证通过，提前告知并覆盖安装真机后，用户已确认验收通过并授权提交与推送。2026-11-10 同版本字根修正的独立状态见文末补记；原版验收及提交授权不代替本次验收。

## 本版行为

- 设置新增“输入符号后返回键盘”，位于输入习惯，默认开启；符号页输入一个符号后回到原语言主键盘，关闭后保留页面和分类供连续输入。
- 只对符号网格选择生效，不影响分类切换、符号页空格/退格/回车、主键盘标点及 emoji 连续输入。
- 复用既有引擎提交/session 校验，非搜索、确有提交、所有平台 `commitText()` 返回 true 且编码为空时才通知返回；不扩展为宿主落字确认或失败回滚协议。
- 新增 preferences 键 `symbol_auto_return`，不改表、不清库，旧安装默认启用；应用 ID、权限、签名和资源保持。

## 电脑端验证

- 执行 `tools/build_debug.ps1 -Offline` 通过，完成资源检查、Debug 与 androidTest 构建、JVM 测试和 Lint。核对报告：103 项 JVM 测试，0 失败、错误与跳过；Lint XML 0 项问题。
- 在保留的 `fansonIME_API31` / `emulator-5580` 执行 `tools/test_emulator.ps1 -Serial emulator-5580`，首轮退出码 0，`count=105`、`INSTRUMENTATION_CODE: -1`，无 failure 字段。覆盖原有 100 项及五组新检查：开关开启；三语言真实触摸完整多字符符号后返回原语言；分类/空格/退格/回车不返回；关闭持久化与连续输入保留分类；两种开关状态下 emoji 连续输入和重新开启生效。
- 收尾时改进默认值用例：初始测试预写 true，无法检测默认值回归；现改为只在隔离模拟器中删除 `symbol_auto_return` 键并确认缺失，再创建页面和启动输入。随后执行 `:app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain` 通过，完整重跑 `tools/test_emulator.ps1 -Serial emulator-5580` 再次通过：退出码 0，`count=105`、`INSTRUMENTATION_CODE: -1`，无 failure 字段。应用 APK 哈希保持，未重装真机。
- 已查看并归档 [设置开关](../screenshots/41-symbol-auto-return-setting-0.3.3.png)、[输入后主键盘](../screenshots/42-symbol-auto-return-keyboard-0.3.3.png)，说明完整、开关开启，完整“……”输入后主键盘语言保持五笔。截图只含模拟器合成内容。
- 最终 APK 应用 ID `com.fanson.ime`，版本 0.3.3 / 18，名称“放松输入法”；仅申请 `VIBRATE`。v2 签名通过，证书 SHA-256 与 0.3.2 相同。

## APK 与真机

- 归档 `dist/FansonIME-0.3.3-debug.apk`，10,162,387 字节；与回归及安装 APK 哈希一致。SHA-256：`31548281f16f1c3a7e2d2efae0d6c80c6e5da96a99f047209091a25ffaa7ca8b`。
- 提前告知后向 HUAWEI NOH-AN00 / API 31 执行 `install -r`，返回 `Success`。由 0.3.2 / 17 升级至 0.3.3 / 18；首次安装时间仍为 `2026-11-08 13:46:19`，更新时间 `2026-11-09 22:22:23`。
- 已启动并核对 `.settings.MainActivity` 为前台 Activity。未卸载、清库、读写个人输入/剪贴板或更改真机默认输入法；未读取用户数据作比对。
- 用户已确认真机验证通过，并明确授权提交代码与推送；提交前已同步验收状态并检查文档链接及暂存范围。
- 未进行本轮横屏、多宿主、release 或连接拒绝提交专项注入验证；平台返回值检查不保证任意宿主最终落字，G01 限制仍保留。结束本轮隔离模拟器进程，保留测试 AVD 数据目录。

方案见 [符号输入后返回键盘](../technical/proposals/2026-11-09-symbol-auto-return.md)。

## 2026-11-10 字根修正补记

版本名及 versionCode 均保持 0.3.3 / 18。用户已确认本次真机验收通过，并授权将字根修正与项目许可证、开发约定一起提交；本次提交信息按用户要求采用 `docs:` 中文前缀，未授权推送。

### 修正范围与来源

- 仅覆盖 B/L/O/P：B 补耳；L 移除误提取的 `{`、恢复田、补力及繁体車；O 移除 `{`、恢复火；P 补“礻去末点／衤去末两笔”的共同非成字根，保留首点。
- 完整复核这四键的现有形态及基本字根，另发现并补齐 L 的繁体車。事实参考 [五笔拆解查询字根表](https://hantang.github.io/search-wubi/wiki/roots/)及其繁简字根图、[Benber 字根说明](https://wubi.benber.net/guide/roots)。只核对键位和笔形事实，未复制这些参考的图片或字体。
- `third_party/wubi_roots/keys.json`、原始字体哈希及锁记录保持；本地校正独立存入 `tools/wubi_root_corrections.json`。旧提取器使用单字符正则读取 TeX 的 `\C {\hskip 1em 田〇}` 和火的同类写法，错误保留 `{` 并漏掉真正字根；新的生成流程应用显式校正，避免再次生成旧错误。
- 当前 25 键、262 项；B 13 项、L 14 项、O 6 项、P 7 项。其他 21 键列表不变。P 的 U+E000 从固定原字体 U+793B 派生，只删除末尾右点的轮廓分支，去掉与已删除点相关的旧 hint 指令；其余现有字形轮廓和字宽保持。历史字体修改声明保留并追加本次声明及许可说明。

### 本次电脑端验证

- `python tools/prepare_root_data.py --source-font .local/UMingCN-wubi-86-kb.ttf`：通过，生成 262 项及 84,952 字节字体。开发时曾遇到 GlyphCoordinates 不支持切片删除及新字形缺少纵向度量两处生成错误，修正后生成通过；原始字体及上游资料未改。再次生成字体与 JSON 的 SHA-256 均一致。FontTools 输出丢弃不参与字形显示的 FFTM 表提示。
- `python tools/verify_assets.py`：通过。核对资源及原始来源哈希、四键必需形态、无花括号、其他键原列表、数据和隐私静态约束。
- `python tools/verify_root_font.py --source-font .local/UMingCN-wubi-86-kb.ttf`：通过，262 个所需码点都有字形，原有轮廓及横向字宽与固定原字体相同；新增 P 字形保留首点及其余笔形，仅去掉右点。
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_debug.ps1 -Offline`：通过。Debug / androidTest 构建成功；103 项 JVM 测试，0 失败、错误、跳过；Lint XML 0 问题。同步 APK 内许可说明后再次执行同命令通过，单元测试及未变任务为 UP-TO-DATE。编译仍有测试中既有 SHOW_FORCED 弃用提示。
- 复用并保留 `.local/avd/fansonIME_API31.avd`，通过 `tools/start_test_emulator.ps1` 启动 `emulator-5580`，随后执行 `tools/test_emulator.ps1 -Serial emulator-5580`：退出码 0，`count=106`、`INSTRUMENTATION_CODE: -1`，无 failure 字段。本次新增四键卡片检查，核对完整字根列表、实际字体有字形、P 无障碍描述、原编码与正文保留；原有 105 项回归通过。启动期间出现软件 GPU 的 `s_glBindAttribLocation` 输出，测试仍正常完成。
- 已人工查看并归档 [L 卡片](../screenshots/43-corrected-roots-l-0.3.3.png)、[O 卡片](../screenshots/43-corrected-roots-o-0.3.3.png)、[P 卡片](../screenshots/43-corrected-roots-p-0.3.3.png)、[B 卡片](../screenshots/43-corrected-roots-b-0.3.3.png)：新增笔形清晰，无 `{`、缺字方框或裁切；截图仅含模拟器合成数据。
- `aapt dump badging` 与 `apksigner verify --verbose --print-certs`：最终 APK 仍为 `com.fanson.ime` / 0.3.3 / 18，仅 VIBRATE 权限，v2 签名通过；证书 SHA-256 与原版相同（`ab6097e0e833022396da5aa6d697e8b065dee781ff64ac16c586105e764d4ba2`）。

### 本次产物与真机验收

- 单独归档 `dist/FansonIME-0.3.3-roots-debug.apk`，保留原版归档，文件名后缀仅区分同版本产物；10,272,328 字节，SHA-256：`722fc5cabc7be4f006ae702bfe8a84538ddc11adeefe14c10c8645390c8fd31d`。
- 提前告知后，核对已连接 HUAWEI NOH-AN00 / API 31 的已安装 0.3.3 / 18，备份其 APK 至忽略目录 `.local/` 并用 apksigner 核对签名与本次包一致；执行 `adb -s <已核对真机序列号> install -r dist/FansonIME-0.3.3-roots-debug.apk` 返回 `Success`。版本仍为 0.3.3 / 18，首次安装时间仍为 `2026-11-08 13:46:19`，更新时间 `2026-11-10 21:17:06`；已启动 `.settings.MainActivity` 供用户验收。
- 未卸载、清库或操作真机个人正文、剪贴板；未使用模拟器脚本操作真机。用户已确认真机验收通过并授权提交，未授权推送。隔离模拟器回归结束后已停止进程，保留原 AVD 数据目录。
- 最终归档与构建 APK 哈希一致，包内字根 JSON、字体、元数据及许可说明与工作区一致；`git diff --check` 及本次修改 Markdown 的本地文件链接检查通过。
- 本轮尚未验证横屏、多宿主、release 或无障碍朗读实际效果；没有宣称穷尽所有生僻变体。新字形实际显示已在 API 31 隔离模拟器查看，真机显示以用户验收为准。

## 2026-11-10 项目许可证补记

- 用户授权在项目根目录添加 Apache-2.0 `LICENSE`，明确要求暂不提交 Git。README 声明原创代码和文档的许可范围，第三方声明同步说明资源及其派生产物继续遵循原许可；已有字根修正工作保留。
- 使用 `python -` 内联脚本从 `https://www.apache.org/licenses/LICENSE-2.0.txt` 获取官方完整文本，原样写入 `LICENSE`，包含条款与附录，共 11,358 字节。SHA-256：`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`。
- 执行 `python -` 内联检查：本地 `LICENSE` 与下载文本哈希一致，README 和第三方声明的主许可证链接正确，第三方 LGPL-3.0、CC BY-SA 4.0、ARPHIC 许可文件及 APK 许可目录存在，结果通过。
- `git diff --check -- README.md docs/THIRD_PARTY_NOTICES.md docs/validation/README.md docs/validation/VALIDATION-0.3.3.md`：通过。
- 本次仅新增许可证及文档说明，未运行构建、单元测试、Lint 或设备测试；未修改运行时代码及第三方许可原文。文档检查不代表已完成最终 APK 分发合规或完整授权链审查。未执行 Git 暂存、提交或推送。

### 验收后许可证复核与提交准备

- 用户随后确认字根修正真机验收通过，授权连同根目录 LICENSE 一起提交，并要求后续持续遵守 Apache-2.0、写入 AGENTS.md；本次提交信息指定 `docs:` 开头的中文，未授权推送。上一节“暂不提交”保留为此前许可证添加阶段的历史状态。
- 本次重新执行 `python -` 从 [Apache 官方文本](https://www.apache.org/licenses/LICENSE-2.0.txt) 读取内容并逐字节比较：本地 LICENSE 为 11,358 字节，SHA-256 仍为 `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`，与官方全文完全一致，无缺失或额外条款，未改许可证正文。附录中的 `[yyyy]` 和 `[name of copyright owner]` 是官方应用示例，保持原文。
- AGENTS.md 新增持续遵守 Apache-2.0、保留适用声明、核对新增来源及兼容性、维护分发许可资料等要求，并明确第三方及派生产物保留原许可。核对依据为 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 与 [ASF 许可 FAQ](https://www.apache.org/foundation/license-faq.html)；未将第三方字体、词库改授 Apache-2.0。
- 本阶段仅修改开发约定和验收/许可文档，未重新构建或操作设备；上节构建与测试结果属于此前实际执行的字根修正验证，不作为本阶段新运行结果。本阶段重新执行 `python tools/verify_assets.py`、文档本地文件链接检查及 `git diff --check` 均通过；提交前检查暂存内容排除本机配置、密钥、私有数据和构建产物。
