# 0.1.5 验证记录

日期：2026-11-08。用户已验收 0.1.4 功能。

## 变更

- 主键盘显示编码提示与候选栏。
- 剪贴板、emoji 浏览、符号、数字页面同时隐藏两者，使用 GONE 移除占位；返回主键盘恢复。
- Emoji 五笔关键词搜索调出输入键盘时保留编码与候选，供组成搜索词；表情浏览时隐藏。
- 不改变候选排序、编码处理、个人数据存储及面板内功能。

## 验证

- `tools/build_debug.ps1 -Offline`：Debug 与仪器测试 APK 编译成功；41 项单元测试通过，0 失败；Lint 无问题；离线资源、权限及备份排除检查通过。
- 沿用项目原 Android 12 模拟器，`tools/test_emulator.ps1 -Serial emulator-5580`：39 项检查通过，包括辅助面板隐藏候选区且不留占位、返回主键盘恢复，以及 emoji 搜索选字。
- 原有五笔选字/展开、英文、回车、Shift、emoji 频率与单页、符号分类、剪贴板历史和服务重绑定检查通过。
- 原生视图检查等待词库就绪和异步面板切换后的布局完成，仅检查当前可见窗口，避免冷启动或旧窗口影响断言。
- 已查看常用 emoji 与剪贴板截图；工具栏下直接显示面板内容，无编码提示和候选栏空位。截图归档于 `screenshots/14-emoji-no-candidates-0.1.5.png`、`15-clipboard-no-candidates-0.1.5.png`、`16-symbols-no-candidates-0.1.5.png`。

## Debug APK

- `dist/fansonIME-0.1.5-debug.apk`
- versionCode=6，versionName=0.1.5，应用 ID 仍为 `com.fanson.ime`。
- 大小：7,820,814 字节。
- SHA-256：`e4ae2d54afce478d53f87fe9dd24983e4d127aea80bd66f06483ddff7223772a`。

## 真机

已在最终回归通过后提前告知用户，并完成真机覆盖安装。包信息确认 versionCode=6、versionName=0.1.5；保留设置、手动短语与剪贴板历史，未通过 ADB 读取或清空真机剪贴板。原模拟器已关闭并保留数据。
