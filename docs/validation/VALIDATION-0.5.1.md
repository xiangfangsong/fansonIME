# 0.5.1 / 23 验证记录

日期：2026-11-13。范围：[个人 JSON 备份](../technical/PERSONAL_BACKUP.md)。基于 `f3ab6c3`，无新增权限、运行时依赖、schema 或第三方内容。

状态：电脑端验证通过，已于 2026-11-13 按用户指示覆盖安装真机；用户随后明确确认真机验收通过并授权提交，未授权推送。

## 实际命令与结果

- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_debug.ps1 -Offline`：最终通过资源校验、Debug 构建、156 项 JVM（0 failures / 0 errors）、零问题 Lint、测试 APK 编译。新 JVM 文件 BackupFrequencyTest 含 3 项。
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/start_test_emulator.ps1`：启动并保留项目已有 `fansonIME_API31.avd`，仅操作指定 `emulator-5580`。测试脚本校验 qemu，关闭隔离模拟器网络，未操作真机。
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/test_emulator.ps1 -Serial emulator-5580`：177 项完整回归通过，INSTRUMENTATION_CODE: -1、无 failure，脚本退出 0。包括 17 项备份检查及原五笔、拼音、英韩、标点长按、候选、emoji、剪贴板与输入生命周期回归。
- `powershell -NoProfile -ExecutionPolicy Bypass -File tools/test_emulator.ps1 -Serial emulator-5580 -Focus backup`：首次 15 组专项通过，补充真实 SAF 与 Activity 重建后 17 项通过；最后补充生成式 64 MiB 超限输入与截图帧同步，再跑 17 项通过，INSTRUMENTATION_CODE: -1、无 failure，脚本退出 0。只修改测试 APK，主 APK 与完整回归相同。
- `E:/Android/SDK/build-tools/36.0.0/aapt.exe dump permissions app/build/outputs/apk/debug/app-debug.apk`：最终 APK 只声明 android.permission.VIBRATE。
- `E:/Android/SDK/build-tools/36.0.0/apksigner.bat verify --print-certs app/build/outputs/apk/debug/app-debug.apk`：Debug 签名验证通过，证书 SHA-256 为 `ab6097e0e833022396da5aa6d697e8b065dee781ff64ac16c586105e764d4ba2`。
- `git diff --check` 退出 0；检查 README、docs 顶层/technical、新方案和本版验证的本地 Markdown 链接，无失效目标。

## 检查范围

四类 JSON 往返、单类省略、缺失与空类别、损坏/重复/版本/类型/数值/递归限制、设置白名单；短语去重与原数据保留；emoji 次数及同分最近顺序；选择隔离；SQLite 人为触发器失败时整批回滚；共享词频未落盘快照、还原代数和隐藏保存不复活旧值。

界面检查四类复选框、空选择禁用、缺失项灰显未勾选、空类别可选；通过 ActivityMonitor 拦截的 URI 返回用于确定性验证分类与导出内容，这部分不冒充真实 SAF 选择。另通过真实 DocumentsUI 保存唯一合成 JSON、选回、点击确认导入，并核对短语重新出现；无新增权限或权限请求。Activity.recreate 后载入内容、勾选状态及禁用项保留。测试只清理自己唯一命名的合成导出文件。

[缺失类别灰显截图](../screenshots/54-backup-missing-categories-0.5.1.png) 已从隔离模拟器导出并目视检查：四项和按钮完整显示，缺失三项灰显且无法勾选，无裁切。最初截图早于重建后的可见帧而拍到桌面，已改为等待真正获得焦点的对话框和显示帧；没有将旧图归档。

## 测试过程修正

首次跨应用返回键使用 Instrumentation.sendKeyDownUpSync，被 Android 拒绝 INJECT_EVENTS；改为 UiAutomation 全局返回，无应用权限变更。失败遗留 DocumentsUI 置顶导致后续 startActivitySync 等待，停止的仅是隔离模拟器中的本应用和 DocumentsUI，再运行通过；未删除 AVD。新增 SetTextI18n 警告通过资源占位文案修正。Windows PowerShell 的 `*>` 外层重定向会把模拟器 OpenGL 标准错误消息记作 NativeCommandError，曾导致已通过的 instrumentation 外层退出码为 1；最终完整与专项采用 Python subprocess 的参数数组调用同一脚本并合并捕获输出，保留真实退出码，二者均为 0。

## 产物和未验证项

### 2026-11-13 真机覆盖安装

用户明确要求“安装真机”，执行前已告知。核对本次唯一在线真机 NOH_AN00 / API31，原版本 0.5.0 / 22；从 `pm path com.fanson.ime` 返回的安装路径只拉取 base.apk 到被忽略的 `.local/` 验证签名，证书 SHA-256 与本版一致。未读取个人库或剪贴板。

定向执行 `adb -s <已核对真机序列号> install -r dist/FansonIME-0.5.1-debug.apk`（设备序列号在共享记录中脱敏），返回 `Success`。随后用同一目标的 `dumpsys package com.fanson.ime` 核对 versionName 0.5.1 / versionCode 23，设备报告 lastUpdateTime 2026-11-13 22:27:08。采用保留数据的覆盖安装，未卸载、清数据或运行模拟器测试脚本，也未在真机导入/导出或修改个人记录；用户随后明确反馈“可以的，真机验证通过，可以提交代码”。

Debug 输出 `app/build/outputs/apk/debug/app-debug.apk`，归档 `dist/FansonIME-0.5.1-debug.apk`；二者及完整回归主 APK 的 SHA-256 相同：`86020370630a0de143e654d4f7387276cd6954c2a6cb92c924f6d7874af5f3cd`。应用 ID com.fanson.ime，versionName 0.5.1 / versionCode 23，minSdk 31、targetSdk 35。无 schema 升级，个人库仍 version 2。

未验证：其他厂商 DocumentsUI/第三方云文件提供方、真实设备进程回收与断电、release 包、长期性能。无加密、外部输入法格式转换、内置词库更新或自动备份，文件写入失败可能留不完整文件并提示重试。emoji 显示仍受系统字形影响。

## 2026-11-13 用户验收与提交授权

用户明确确认本版真机验证通过并允许提交代码。同步更新产品、技术与验证文档中的验收状态；本轮没有修改已验收的应用实现或 APK，因此未重复编译或运行测试，前述各项仍为本次功能开发中实际执行的结果。仅授权本地 Git 提交，未授权推送。
