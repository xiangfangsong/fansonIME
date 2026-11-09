# 0.3.3 验证记录

日期：2026-11-09；版本：0.3.3 / versionCode 18。状态：电脑端及默认值升级场景验证通过，提前告知并覆盖安装真机后，用户已确认验收通过并授权提交与推送。

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
