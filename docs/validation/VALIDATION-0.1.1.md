# 0.1.1 验证记录

日期：2026-11-08。

## 本版范围

- 可见名称统一为“放松输入法”，应用 ID 仍为 `com.fanson.ime`，versionCode 升为 2，覆盖升级保留既有设置、短语和收藏。
- 最近 10 项普通文本剪贴板历史：去重置顶、淘汰最旧、持久保留、历史粘贴和单项删除；一键清空同时清除系统当前内容和全部本地历史。
- 记录默认输入法服务实际接收到的复制事件，包括面板外和键盘隐藏时；锁屏、隐私输入会话及敏感标记不自动入库。
- 历史数据位于 `noBackupFilesDir/personal.db`，不上传、不备份、不写日志。升级前或未接收到的历史无法补回。
- 不改动五笔规则、emoji 等功能；拼音留待后续。

## 已完成检查

- `tools/build_debug.ps1 -Offline`：Debug APK、测试 APK、18 项单元测试及 Lint 全部通过，Lint 0 问题。
- `tools/verify_assets.py`：码表、emoji、校验值、权限和备份规则通过。
- 最终 APK 权限：仅 `android.permission.VIBRATE`，无网络权限。
- 原独立 Android 12 模拟器 `fansonIME_API31` / `emulator-5580` 上，关闭 Wi-Fi 与移动数据，21 项检查全部通过。

模拟器检查覆盖：原有五笔、英文、emoji 输入和删除、独立 emoji 搜索、当前剪贴板查看及粘贴；新增名称、历史容量与顺序、重复置顶、敏感与空白排除、数据库重新打开后的保留、历史单项删除、面板外复制、键盘隐藏复制、历史粘贴，以及当前与历史同时清空。敏感内容主动显示及清空仍通过。

界面截图见 `screenshots/06-clipboard-history-0.1.1.png`。

## Debug APK

- 路径：`dist/fansonIME-0.1.1-debug.apk`
- 大小：7,807,056 字节。
- SHA-256：`f136932f0aba22e17dc8e246935beac26d5d641b35e643915793096137084db0`

## 真机

提前告知后，已在 HUAWEI NOH-AN00 上通过 `adb install -r` 覆盖安装，返回 `Success`；包信息确认 versionCode=2、versionName=0.1.1，已打开应用首页。未卸载旧版，既有设置和短语保留；没有通过 ADB 读取、清空或写入真机剪贴板。最近 10 项历史的真机使用体验待用户反馈。

测试后关闭模拟器，按用户要求保留其数据，不删除。
