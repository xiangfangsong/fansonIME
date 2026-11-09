# 0.3.1 验证记录

日期：2026-11-09。当前版本 0.3.1 / versionCode 16，Android 12+。按用户反馈将长按提示移到上方并补齐英文/韩语；已完成本轮验证并提前告知后覆盖安装真机，用户已确认验收通过并授权提交；未推送。

## 本版行为与实现

- 主键盘空格显示“␣＋当前语言”；符号页三种语言均只显示“␣”，点击仍输入空格，返回主键盘恢复语言标签。
- 三种语言主键盘短按逗号、句号保持原标点，中文长按分别输入全角“！”“？”，英文/韩语输入半角 `!`、`?`；长按提示在原符号上方，长按消费事件，松手不追加短按标点、不连发。
- 共用 `Ui.punctuationButton()` 与服务 `punct()`，保留首候选确认、无匹配保护和内部搜索目标隔离；空格标签由 `spaceButton(showLanguage)` 明确控制，不改变引擎。
- 关于页版本从 `PackageManager` 读取当前安装包元数据，避免内联旧版本显示，并与实际安装版本做仪器核对。
- 应用 ID、组件、签名配置、设置与个人数据协议和所有字典/字体资源保持。技术文档、产品稿、版本说明同步更新。

## 修订构建 16

本轮重新执行构建、完整模拟器回归、上方提示截图检查及签名/安装核对。新增覆盖英文、韩语预编辑后长按半角标点，并将原标点触摸用例扩展到三种语言；此前构建 15 的通过结果不视为当前修订已通过。

- `tools\build_debug.ps1 -Offline` 成功，103 项 JVM 测试通过（0 失败、错误及跳过），Lint XML 0 项问题，Debug 与 androidTest APK 编译完成。
- 源 Manifest、字典/资源元数据、数据与引擎实现保持；资源及文档链接检查通过。最终 APK 仅申请 `VIBRATE`，应用 ID `com.fanson.ime`、版本 0.3.1 / 16。
- 最终归档为 `dist/FansonIME-0.3.1-debug.apk`，10,153,591 字节，与实际测试及安装 APK 的哈希一致。SHA-256：`f90141ac802f3e127088fbf8651c9e3eacd754dfddfc72c2a113508e698e27bd`；v2 签名校验通过，证书与构建 15 一致。
- 本轮首次回归在符号页空格断言处中断：原 `checkCandidateArea(false)` 在空闲主键盘和符号页都成立，无法证明异步面板切换已完成。测试已增加“返回键盘”入口出现的等待，并等待标点两行布局就绪后再核对上方提示，重新执行完整回归；应用 APK 未因此变动。
- 修正等待条件后，执行 `tools\test_emulator.ps1 -Serial emulator-5580`，退出码 0，`count=93`、`INSTRUMENTATION_CODE: -1`，无 failure 字段。93 项完整检查通过，覆盖三种语言上方提示、真实长按松手不额外输入，以及英文/韩语预编辑确认后分别得到 `a!b?`、`가!나?`；原有功能回归通过。
- 已人工检查本轮截图：[中文上方提示](screenshots/36-punctuation-long-press-0.3.1.png)、[英文上方提示](screenshots/37-english-punctuation-0.3.1.png)、[韩语上方提示](screenshots/38-korean-punctuation-0.3.1.png)。均为小字号长按符号在上、原标点在下，中文全角与英文/韩语半角输出正确。
- 提前告知后向 HUAWEI NOH-AN00 / API 31 执行 `install -r`，返回 `Success`；安装后核对版本 0.3.1 / 16，首次安装时间不变，已打开 `.settings.MainActivity`。未卸载、清库、通过 ADB 输入测试正文、读写剪贴板或改默认输入法；未读取个人数据做比对。
- 2026-11-09 用户确认真机验证通过并授权提交，要求提交信息以 `feat:` 开头且使用中文，已写入根目录 `AGENTS.md`。未做本轮横屏、多宿主应用或 release 专项验证；本次只授权提交，不推送。

## 首次构建 15 的验证（历史）

以下为用户提出修订前的实际验证与安装记录。旧 APK 原归档名为 `FansonIME-0.3.1-debug.apk`，修订归档前另存为 `dist/FansonIME-0.3.1-build15-debug.apk`，保留原哈希。

### 电脑端检查

- `powershell -NoProfile -ExecutionPolicy Bypass -File tools\build_debug.ps1 -Offline`：成功完成资源校验、Debug APK、androidTest APK、JVM 测试和 Lint。
- JVM XML 报告共 103 项，0 失败、0 错误、0 跳过；Lint XML 为 0 项问题。没有为 UI 行为增加镜像式 JVM 测试，新增回归在仪器层覆盖真实事件。
- 源 Manifest、数据锁记录、资源元数据与基线一致，资源完整性通过；本地文档链接与格式检查通过。

### 模拟器回归

沿用原 `fansonIME_API31` / `emulator-5580`，不连接真机执行仪器测试。第一轮已验证空格和真实长按松手，随后因新用例误用 `z` 文案定位中文 Z 图标而失败；已改用现有“Z / 查读音”无障碍入口。该轮还发现关于页版本滞留旧值，已改为读取安装包版本并补回归。

第二轮关于版本检查通过，但系统 `com.android.bluetooth` 崩溃弹窗抢占焦点，测试在请求试打框焦点时中断；失败截图与 `mCurrentFocus` 已确认。仅在该隔离模拟器执行 `svc bluetooth disable` 并关闭弹窗后，完整重跑；原 AVD 数据目录保留，真机蓝牙未操作。

最终执行 `powershell -NoProfile -ExecutionPolicy Bypass -File tools\test_emulator.ps1 -Serial emulator-5580`：退出码 0，`count=92`、`INSTRUMENTATION_CODE: -1`，无 failure 字段，92 项检查全部通过。

- 新增 5 组检查：关于页版本一致性；三种语言空格显示和符号页空格实际输入；中文短按与真实长按松手不额外输入；有候选先确认和无匹配保留；emoji 五笔搜索目标隔离。
- 原有五笔/读音、简繁、英文、韩语、数字页、字根、emoji、符号、剪贴板与生命周期回归通过。
- 人工检查并归档当时截图：[符号页空格](screenshots/35-symbol-space-0.3.1.png)、[构建 15 主键盘长按标点](screenshots/36-punctuation-long-press-0.3.1-build15.png)。确认符号页无模式名称，主键盘保留模式，长按提示和实际“，。！？”输入均可见；长按提示在下方的布局已按反馈在构建 16 修订。

### APK 与真机

- 首轮原归档为 `dist/FansonIME-0.3.1-debug.apk`；当前保留副本为 `dist/FansonIME-0.3.1-build15-debug.apk`，10,187,516 字节，与当时构建输出 SHA-256 一致。
- SHA-256：`f1618e22d586769ddb665626a08694132e30713fa3eef9453767e1841d1ca5e5`。
- `aapt dump badging/permissions`：应用 ID `com.fanson.ime`、versionCode 15、versionName 0.3.1，中文名称“放松输入法”，仅 `VIBRATE` 权限。
- `apksigner verify --verbose --print-certs`：v2 校验通过，签名证书与原 0.3.0 归档相同，未改签名配置。
- 真机：HUAWEI NOH-AN00 / API 31，从 0.3.0 / 14 执行 `adb -s <已确认真机序列号> install -r dist\FansonIME-0.3.1-debug.apk`，返回 `Success`。
- 安装后核对 versionCode=15、versionName=0.3.1，首次安装时间保持不变，已启动 `.settings.MainActivity`。未卸载、清库、通过 ADB 改默认输入法、输入测试正文或读写真机剪贴板；个人数据保留由覆盖安装方式保障，未读取用户数据做比对。
- 等待用户验证实际交互；本轮未做 release 构建、横屏及多宿主应用新增专项验收。暂不提交或推送 Git。
