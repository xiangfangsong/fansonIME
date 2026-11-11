# 0.4.1 轻震动强度与作者邮箱验证

日期：2026-11-10；versionName **0.4.1**，versionCode **20**；基于 `00bb608`。本次离线构建、123 项 JVM、128 项完整模拟器检查、资源校验与零问题 Lint 均通过。已覆盖安装真机，用户于 **2026-11-11 确认真机验收通过并授权 Git 提交**；未授权推送。

## 实现与维护边界

- 原 `haptic=false` 开关保留；新增 `haptic_strength=50`，范围 0–100%，0 不振动。缺失/非法值回退 50，超界值夹到范围，旧开关和用户其他数据保留；仍使用 personal.db version 2 的 preferences 表，没有数据库迁移。
- 关闭开关禁用滑动条但保留强度。拖动只更新百分比，松手保存并预览一次；无障碍/方向键调整每次保存和预览。程序初始化、设置重建不会触发预览。
- UI 预览与服务共用 [HapticFeedback](../../app/src/main/java/com/fanson/ime/keyboard/HapticFeedback.kt)：支持振幅时 10ms、振幅按百分比映射至合法 1–255；不支持时使用系统默认振幅和 1–20ms 脉冲，50% 为 10ms。0% 或没有马达不构造效果。
- 服务在原输入生命周期读取并缓存 VibrationEffect；每次按键不解析偏好、不查询硬件能力、不新建效果，沿用至少 30ms 的触感间隔，不限制正文输入速度。没有定时任务、新依赖或新权限。
- 关于页显示可选择复制的 `作者邮箱：cnxfs@foxmail.com`，不配置发送动作、不自动启动外部应用。原创代码及文档继续遵守 Apache-2.0，已有第三方许可不变。
- UI 与平台依据见 [界面与面板](../technical/UI_AND_PANELS.md#041-轻震动强度与关于页)，设置键见 [数据与隐私](../technical/DATA_AND_PRIVACY.md)。

## 实际执行

使用现有 JDK/SDK 和 `.local/avd/fansonIME_API31.avd`，未删除或重建 AVD；测试数据只写明确指定的隔离 `emulator-5580`。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_debug.ps1 -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/start_test_emulator.ps1
adb -s emulator-5580 shell getprop sys.boot_completed
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test_emulator.ps1 -Serial emulator-5580
```

- 本次离线构建包含 Debug、androidTest、JVM、Lint 和资源校验；123 个 JVM 测试通过，0 failures/errors/skipped；Lint XML 0 个问题。
- 新增两项 HapticFeedbackTest，覆盖全部 1–100% 在两种硬件能力下的合法/单调参数、0% 静音、默认值及非法/超界偏好。
- 新增四组仪器检查：缺少强度键时默认 50% 且关闭门控；真实 DOWN/MOVE/UP 拖动过程中不写库、松手保存以及无障碍 0/100% 调整；设置重建与新 LocalStore 连接保留强度和开关；作者邮箱完整且可选择。
- 最终完整模拟器回归 **128 项通过**，脚本 exit 0，`INSTRUMENTATION_CODE: -1` 且无 failure；包含本版新增四组检查和原有 124 项回归。检查结束恢复轻震动关闭、强度 50%、主题跟随系统，避免影响后续用例。0.4.0 词频、独立清空及旧功能回归本次也执行，不复用上次通过结果。
- 日志：`.local/haptic-build-0.4.1.log`、`.local/haptic-emulator-0.4.1.log`；首轮失败日志保留在 `.local/haptic-emulator-first-0.4.1.log`。

### 开发中修正

初次界面检查在注入 MOVE 后立刻读取进度，读到尚未更新的值而失败；改为等待实际进度变化，随后该拖动及松手持久化场景通过。未以固定延时猜测控件状态。新增动态百分比最初触发 SetTextI18n 提示，改用三条格式化字符串资源，重新构建后 Lint 0 个问题。

## 界面与最终 APK

仪器中的实际触摸拖动检查曾将强度调到 25%，核对拖动不写库、松手保存已通过。该截图中控件靠近屏幕底部，完整回归结束后重新打开隔离模拟器设置页，仅滚动并截屏，正式归档为：

- [完整强度滑动条与说明](../screenshots/47-haptic-strength-slider-0.4.1.png)：展示恢复默认关闭、50% 的完整控件。
- [关于页版本和作者邮箱](../screenshots/48-author-email-0.4.1.png)：0.4.1 与 cnxfs@foxmail.com 完整可见。

上述两张图已人工查看，没有遮挡或文案截断；只取自隔离模拟器，不含手机个人画面。

最终构建已归档（无本次运行代码后续修改）：

- `dist/FansonIME-0.4.1-debug.apk`，10,281,184 bytes。
- SHA-256：`05dd49b35cc0db61750a452196f17b1331ad8fe04ba4c8714816bee424ea9b90`；同目录保留 `.apk.sha256`。
- aapt 实查 `com.fanson.ime`、0.4.1 / 20、应用名“放松输入法”；权限仍仅 `android.permission.VIBRATE`。
- apksigner 验证通过，v2 签名有效，证书 SHA-256：`ab6097e0e833022396da5aa6d697e8b065dee781ff64ac16c586105e764d4ba2`，与旧安装一致。

```powershell
Get-FileHash dist/FansonIME-0.4.1-debug.apk -Algorithm SHA256
E:/Android/SDK/build-tools/36.0.0/aapt.exe dump badging dist/FansonIME-0.4.1-debug.apk
E:/Android/SDK/build-tools/36.0.0/aapt.exe dump permissions dist/FansonIME-0.4.1-debug.apk
E:/Android/SDK/build-tools/36.0.0/apksigner.bat verify --verbose --print-certs dist/FansonIME-0.4.1-debug.apk
```

这些是本次实际产物检查；用户已另行确认真机验收通过。没有 Release 构建验证，不能将 Debug 验收扩写为 Release 已验收。

## 真机与未验证项

2026-11-10 已提前告知进入真机交付，核对 HUAWEI NOH-AN00 / API31 的旧安装为 0.4.0 / 19，旧包与新包证书一致。只拉取应用代码包备份到 `.local/phone-before-haptic-0.4.1.apk`，旧 APK SHA-256 为 `b509938aaccb719c26e33b4b76f601a3961991002172ed45457ac941b8db2f2b`，没有读取或备份个人数据库。

实际执行以下命令，文档用 `$PhoneSerial` 代替设备唯一序列号：

```powershell
adb -s $PhoneSerial install -r dist/FansonIME-0.4.1-debug.apk
adb -s $PhoneSerial shell dumpsys package com.fanson.ime
adb -s $PhoneSerial shell am start -W -n com.fanson.ime/.settings.MainActivity
```

覆盖安装返回 Success；确认版本为 0.4.1 / 20，首次安装时间保持 `2026-11-08 13:46:19`，更新时间为 `2026-11-10 23:12:47`。首页启动返回 Status ok。没有卸载、清库、切换默认输入法、修改手机振动开关或注入测试正文，没有用模拟器测试脚本操作手机。

2026-11-11，用户明确反馈“真机已验收通过，提交git吧”，本版据此记录真机验收通过并获准提交；不据此声称跨设备、所有强度等级或电量已完成量化测试。

模拟器可验证映射、控件与保存逻辑，**不能证明实体马达的强弱差异或每级百分比的实际触感**。不支持振幅控制的设备按时长近似，实际感受也受系统和硬件影响；50% 不是对旧版 DEFAULT_AMPLITUDE 的逐设备等价保证。本版真机验收已获用户确认；不同型号和全部强度等级的量化对比仍未执行。

没有新增电量基准或长时振动测试；本次不调整五笔词频参数或算法。电脑端检查与本版用户真机验收分别记录；当前已获提交授权，推送仍需单独授权。


## 本轮提交前检查

2026-11-11 仅同步实际验收状态、安装记录并归档已检查的模拟器截图，没有进一步修改运行代码、算法参数或版本。提交前重新检查资源、Markdown 本地链接、暂存差异和文件范围；没有将开发阶段的 123 项 JVM、128 项模拟器和 Lint 结果冒充本轮重新运行。提交标识以 Git 历史为准，未授权推送。
