# 验证记录

日期：2026-11-08。开发版本：0.1.0 Debug。

## 已完成的电脑端验证

- `:app:assembleDebug`：最终 Debug 构建成功；所有构建依赖缓存后，最终检查使用 `-Offline` 完成。
- `:app:testDebugUnitTest`：18 项测试通过，0 失败；覆盖五笔简码、四码唯一、重码、第五码、纠错、标点、会话清理、词组取码及密码/数字字段识别。
- `python tools/verify_assets.py`：通过；检查 136,239 条码表、25 个一级简码首选、3,781 个 emoji、资源 SHA-256、数据库完整性、网络权限缺失和备份排除规则。
- `:app:assembleDebugAndroidTest`：已编译模拟器测试 APK。
- `:app:lintDebug`：最终报告 0 个问题。
- 最终 APK 的 `aapt dump permissions`：只有 `android.permission.VIBRATE`，没有 `INTERNET`。
- `apksigner verify --verbose`：验证通过，使用 APK Signature Scheme v2 签名。

## Android 12 模拟器交互验证

在项目独立 AVD `fansonIME_API31` / `emulator-5580` 上运行，未使用 USB 真机。关闭 Wi-Fi 和移动数据后，13 项检查全部通过：

1. 内置五笔码表查询及简码首选。
2. 手动短语保存、查询优先级和删除。
3. emoji 离线数据与中文关键词查询。
4. 从软键盘输入五笔并选择候选。
5. 切换英文并输入。
6. 从面板输入 emoji。
7. 删除单个 emoji。
8. 五笔输入 emoji 搜索关键词，搜索内容不进入宿主编辑框。
9. 完整删除家庭 ZWJ、肤色修饰和旗帜序列。
10. 当前剪贴板预览与粘贴。
11. 一键清空后系统剪贴板与面板均为空。
12. 敏感剪贴板默认不显示明文。
13. 主动显示敏感剪贴板后再清空。

测试中使用的是合成文本。界面截图已检查，保存到 `screenshots/`。构建脚本和模拟器脚本的 PowerShell 语法解析通过。

本机 Emulator 37.2.13 的可选网络模拟后端曾阻止启动；仅在本工程测试启动参数中禁用 `Vulkan`、`WiFiPacketStream` 和 `Uwb` 后启动成功，未修改全局模拟器配置。自动化测试已等待最终编码状态，避免选到异步刷新之前的候选。

## 最终 Debug APK

- 文件：`app/build/outputs/apk/debug/app-debug.apk`
- 归档副本：`dist/fansonIME-0.1.0-debug.apk`
- 应用 ID：`com.fanson.ime`
- 版本：`0.1.0` / versionCode `1`
- 最低 API：31；目标 API：35。
- 大小：7,874,271 字节（约 7.51 MiB）。
- SHA-256：`78ca0655e517ec857ad0cef3c47b9b8d2f211316e3b65c74efe6b7b5bb31ec6e`

## 真机状态

已先告知用户，并在用户明确授权后，通过指定 USB 真机序列号将 0.1.0 Debug 安装到 HUAWEI NOH-AN00；ADB 返回 `Success`，已启动应用首页。用户准备手动启用并开始试打，输入兼容性和体验结果待反馈。

没有通过 ADB 切换真机默认输入法、输入正文、读取或清空手机剪贴板。独立测试模拟器按用户要求继续保留。

静态配置和电脑端测试不能替代华为真机兼容性、流畅度及日常应用输入验收；性能目标尚未实测。
