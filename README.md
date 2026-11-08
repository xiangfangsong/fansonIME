# 放松输入法 · fansonIME

个人使用的 Android 12+ 离线输入法，目前以五笔86为主。当前开发版本为 **0.1.6 Debug**，不申请网络权限；未来可加入偶尔使用的离线拼音，本次未实现。

0.1.6 已通过用户验收。

## 首版功能

- 五笔86内置码表：136,239 条记录，简码、词组、候选展开、补全提示。
- 五笔底行“简 / 繁”表示当前输出状态，默认简体，可实时刷新当前候选；繁体单字保留一简多繁的全部映射，词组优先消歧。左上角仍切换五笔与英文。
- 英文提供 46,717 词离线前缀补全（每次最多 80 个），`app` 可选 `apple`；底行 Aa 勾/横线图标开关补全，密码框禁用。点击候选替换当前词，空格提交原词并加空格，回车先确认当前词；不自动纠正拼写错误、不保存英文输入。
- 候选固定单行且不可横向滚动；中文和英文的可见候选都可直接点击上屏。只有一行放不下时出现向下箭头，点击箭头才展开；一行全部放得下时不显示箭头。
- 编码提示和候选仅在主键盘输入时显示；剪贴板、emoji 浏览、符号和数字页隐藏并移除占位。Emoji 五笔搜索调出输入键盘时保留候选选字。
- 空格选词、回车提交原始字母、四码唯一自动上屏、可选第五码顶字、无匹配纠错。
- Shift 从五笔86直接进入英文大写，再按切换英文小写，左上角模式按钮返回五笔86；数字、符号、编辑动作、长按字母首行输入数字、长按空格打开光标控制。
- 主工具栏采用本地矢量图标；收起小三角在设置右侧，删除键盘底部“切换输入法”和“收起”工具行，换行用回折箭头显示。系统输入法选择页仅注册“放松输入法”，不另列五笔86子类型。
- Unicode 16.0 emoji：分类、中文关键词五笔搜索、肤色变体直接选择、按设备字形能力过滤；移除收藏和长按弹层。
- Emoji 常用按本地使用次数排序，最多 30 项且只有一页；同次数按最近顺序排序，无记录时默认补位。当前网格不在点击后立即重排，重新进入常用时刷新。
- Emoji 分类栏可左右滑动但无滚动条；选择分类或翻页重绘后保持原来的水平位置。
- 符号页默认进入常用，提供中文、英文、箭头、数学、序号、单位、货币、括号、图形、希腊和制表等 12 类。英文含完整 32 个可打印键盘标点，多字符符号整项输入；所有分类和符号顺序固定。
- 剪贴板当前内容及最近 10 项普通文本历史、重复置顶、粘贴、单项删除、一键清空；标记为敏感的内容不入历史。
- 浅色、深色、跟随系统、键高、震动、字根提示，手动短语添加和删除。
- 启用引导、输入法选择、本地试打区和数据许可查看。

## 隐私边界

无网络权限、无广告或统计 SDK、无在线资源下载。不保存输入正文历史，输入与剪贴板不写日志。按照用户要求保存的最近 10 项剪贴板文本、emoji 累计次数与最近顺序，以及手动短语和设置，均放在应用私有无备份目录。Emoji 统计不含日期、正文或应用名，隐私输入会话不更新。云备份与设备迁移均配置排除规则，旧收藏数据在升级时移除。

作为系统默认输入法且服务运行时监听复制变化，包括键盘或面板隐藏时；锁屏及隐私输入会话不自动记录，标记为敏感的内容不入历史。历史只记录实际接收到的复制事件，不能找回安装前或系统限制下未捕获的记录。重复复制置顶，超过 10 项淘汰最旧项，重启保留；一键清空同时清除系统当前内容和本输入法历史，不影响其他应用自建历史。输入给目标应用后，由目标应用处理该内容。

## 在本机构建

已使用本机缓存可用的 AGP 8.7.3、Kotlin 2.0.0、Gradle 8.9；compileSdk / targetSdk 为 35，minSdk 为 31。需要 JDK 17，默认系统 Java 8 不能用于该项目。

本机 SDK 地址已经写入被忽略的 `local.properties`。使用其他电脑时配置自己的 `sdk.dir` 或 Android SDK 环境变量。

```powershell
# 自动选择可用的 JDK 17/21，检查资源、编译 APK、运行单元测试和 Lint
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build_debug.ps1

# 构建依赖已缓存后，可全程离线执行
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build_debug.ps1 -Offline
```

输出：

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 单元测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint 报告：`app/build/reports/lint-results-debug.html`
- 仪器测试 APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

首次获取构建依赖需要电脑联网；手机端安装后运行不联网。

## 离线数据

`resources.lock.json` 记录上游 commit、下载 URL 与 SHA-256。`third_party/` 保留原始数据、作者和许可，`tools/prepare_data.py` 负责转换为内置 SQLite 与 JSON。

```powershell
# 已有锁文件与原始数据时，不访问网络
python tools\prepare_data.py
python tools\prepare_completion_data.py
python tools\verify_assets.py
```

只有主动更新数据版本时才重新选择上游 revision。不要手工修改生成后的数据库或校验值来跳过完整性检查。第三方数据来源与修改说明见 [THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)。

## 模拟器测试

测试使用工程目录 `.local/avd/` 中独立创建的 Android 12 AVD，不复用个人手机或已有模拟器数据。测试脚本要求明确的 `emulator-*` 目标，并通过 `ro.kernel.qemu` 再次校验。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\start_test_emulator.ps1
# 等待 adb -s emulator-5580 shell getprop sys.boot_completed 返回 1 后：
powershell -NoProfile -ExecutionPolicy Bypass -File tools\test_emulator.ps1 -Serial emulator-5580
```

脚本会在该模拟器关闭 Wi-Fi 与移动数据、安装两个测试 APK、选择本输入法，并使用专用测试文本验证五笔、英文、emoji 和剪贴板。不要把此脚本用于真机；它会覆盖及清空模拟器当前剪贴板。

## 真机调试安排

用户要求：**进入真机调试前先告知**。已提前告知，并在用户明确授权后，将 0.1.0 Debug 安装到 HUAWEI NOH-AN00 / Android API 31，打开应用首页，等待用户启用并试打。没有通过 ADB 切换真机默认输入法、输入测试文本或读取剪贴板。

本项目继续使用 `.local/avd/fansonIME_API31.avd` 的独立测试模拟器；用户已明确要求保留，不删除。

0.1.1 已在再次提前告知后覆盖安装到上述真机，并打开新版首页，等待用户测试名称与剪贴板历史。

0.1.2 的交互调整详见 [设计文档](docs/DESIGN.md) 的修订表及 [VALIDATION-0.1.2.md](docs/VALIDATION-0.1.2.md)。

真机阶段重点检查华为系统下的启用流程、聊天软件候选及回车行为、剪贴板访问和清空、密码框、导航栏、横屏、连续快速输入与系统回收后恢复。

## 版本与当前范围

见 [CHANGELOG.md](docs/CHANGELOG.md) 与 [VALIDATION.md](docs/VALIDATION.md)。目前尚未包含拼音、拼音反查、自动调频、图片/GIF 表情包和词库导入导出。后续版本以真机反馈逐项推进。

0.1.6 已统一中文和英文候选的直接选择行为，通过 62 项单元测试、54 项模拟器检查和零问题 Lint，提前告知后已覆盖安装真机。Debug APK 为 `dist/fansonIME-0.1.6-debug.apk`；最新验证及安装结果见 [VALIDATION-0.1.6.md](docs/VALIDATION-0.1.6.md)。旧版记录保留在对应版本的验证文档中。

界面截图：[五笔候选](docs/screenshots/02-wubi-candidates.png)、[emoji 搜索](docs/screenshots/03-emoji-search.png)、[剪贴板](docs/screenshots/04-clipboard.png)。

0.1.1 新增：[剪贴板历史界面](docs/screenshots/06-clipboard-history-0.1.1.png)。

0.1.2：[新版键盘](docs/screenshots/01-keyboard-0.1.2.png)、[系统输入法选择页](docs/screenshots/08-input-method-picker-0.1.2.png)。

0.1.3：[候选栏](docs/screenshots/02-candidates-0.1.3.png)、[展开候选](docs/screenshots/09-expanded-candidates-0.1.3.png)、[分类栏保留位置](docs/screenshots/10-emoji-category-position-0.1.3.png)。

0.1.4：[单页常用 emoji](docs/screenshots/11-emoji-common-0.1.4.png)、[常用符号](docs/screenshots/12-symbols-common-0.1.4.png)、[数学符号](docs/screenshots/13-symbols-math-0.1.4.png)。

0.1.5：[表情页隐藏候选区](docs/screenshots/14-emoji-no-candidates-0.1.5.png)、[剪贴板页隐藏候选区](docs/screenshots/15-clipboard-no-candidates-0.1.5.png)、[符号页隐藏候选区](docs/screenshots/16-symbols-no-candidates-0.1.5.png)。

0.1.6：[繁体歧义候选](docs/screenshots/17-traditional-variants-0.1.6.png)、[英文前缀补全](docs/screenshots/18-english-completion-0.1.6.png)、[Aa 自动拼写开关](docs/screenshots/19-english-spelling-key-0.1.6.png)。

设计原稿见 [DESIGN.md](docs/DESIGN.md)。实现选择：设置以小型 SQLite 表代替设计草案的 DataStore，避免增加运行时依赖；所有用户数据仍保存在无备份私有目录。
