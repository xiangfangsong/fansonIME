# 0.4.2 验证记录

最终状态：用户于 2026-11-11 确认 0.4.2 最终真机验收通过并授权提交，未授权推送。以下保留历次测试包的历史证据；最终中文标点包与提交前检查见文末。

日期：2026-11-11；版本 **0.4.2 / versionCode 21**，基于 0.4.1 / `8b51b32`。首次包状态：电脑端验证完成并覆盖安装后，用户反馈 L 字母裁切，**未通过真机验收**；当前修订见文末，始终未提交或推送。

## 变更范围

默认开启的英语句首自动大写、可临时取消的第三种 Shift 图标、32 项常用无撇号缩写固定首选/原词第二、英语 L 长按撇号。保留原词空格/回车、预编辑隔离、五笔/韩语规则。无数据库迁移、依赖、权限或第三方内容增加。完整状态、精确缩写列表、线程/隐私及维护规则见 [维护说明](../technical/ENGLISH_INPUT.md)。

## 本次实际执行

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_debug.ps1 -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/start_test_emulator.ps1
adb -s emulator-5580 shell getprop sys.boot_completed
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test_emulator.ps1 -Serial emulator-5580
```

- 最终离线构建含资源校验、assembleDebug、testDebugUnitTest、lintDebug、assembleDebugAndroidTest，BUILD SUCCESSFUL；12 个 JVM 测试文件，**138 项通过，0 failures/errors/skipped**，Lint XML **0 个问题**。
- JVM 新增 EnglishInputTest（10 项）、EnglishEditorPositionTest（3 项），EditorPolicyTest 增加 2 项；共比 0.4.1 增加 15 项，覆盖三状态、取消/删除、词边界、规范大小写、精确缩写/原词、手动撇号、64 字符和 80 候选限制、选区/预编辑锚点、字段与平台段首返回值。
- 隔离 API31 模拟器 `emulator-5580`，保留项目现有 AVD，不清库/删除 AVD。最终原始仪器结果 **count=138、138 个唯一 passed 标记、INSTRUMENTATION_CODE: -1，无 failure/Process crashed**。包含新增 10 组英语检查及本次重新执行的原 128 项回归，没有沿用历史通过结果。
- 新增实际交互覆盖：空文本/下一句只大写一个字母与快速跨词；取消后删空仍小写、再开持续大写；句末引号/括号/换行与小数；真实光标/选区替换不覆盖旧 composing；关闭拼写后连续直输；开关保存/关闭及缩写保持；缩写点击、原词第二/空格保持；L 真实长按无额外 l、词内编辑/退格；自动首字母与持续大写的缩写候选；密码/两种邮箱/URI 不自动大写但人工 Shift 可用。
- 新功能检查后显式关闭自动大写以保留旧回归的小写断言，整套完成恢复默认 true；所有输入、词频和剪贴板均为合成测试数据，测试脚本仅用于隔离模拟器。
- 最终日志在本机忽略目录：`.local/english-build-r7-0.4.2.log`、`.local/english-emulator-r6-0.4.2.log`。模拟器图形驱动向 stderr 输出 `s_glBindAttribLocation`，外层 Windows PowerShell 将其显示为 NativeCommandError，终端包装返回 1；日志中仪器成功返回如上，未出现脚本的 `Emulator smoke tests failed`。因此记录实际 138 项检查通过，不将外层包装退出码冒写为 0。
- 同步检查 Markdown 本地链接、git diff --check、资源与许可边界；APK 版本/权限/签名实际核对如下。没有新增运行时依赖、权限、第三方数据或字体变更，原 Apache-2.0 与各第三方许可保持。

### 开发中发现与修正

1. 新设置改变页面高度，旧振动拖动测试的触点接近导航区域；增加下方留白，并使用有中间帧的真实 DOWN/MOVE/UP 拖动。ScrollView 中 SeekBar 在越过 touch slop 后才进入 pressed，不等待 DOWN 立刻 pressed；拖动期间不写库、松手保存的断言仍保留，最终通过。
2. 新测试最初在 runOnMainSync 中嵌套了同类查找辅助方法，造成测试进程退出；改用现有主线程查找入口，后续完整回归通过。没有在生产代码增加输入日志。
3. 实际空文本检查发现只检查 CAP_SENTENCES 会漏掉段首：核对本机 Android SDK TextUtils/BaseInputConnection 源码后，兼容只请求句子模式时段首仍返回 CAP_WORDS 的行为；增加单元测试及空文本/换行 UI 回归。普通词间空格仍不触发自动大写。

初轮/中间日志保留在 `.local/english-build-*.log`、`.local/english-emulator-*.log`；不能把中间失败记录视为最终通过结果。

## 安装包与截图

- 首次包归档：`.local/FansonIME-0.4.2-first-debug.apk`（本机忽略目录；dist 同名路径用于最新修订），**10,376,260 字节**，旁有 `.apk.sha256`。
- SHA-256：`4b34cb61f7994b3ed5b247c2013a8ec39b9a64ebb1f68db55a182b6bf1d548f5`；与本次 Gradle 输出一致。
- Build Tools 34.0.0 的 aapt 核对 `com.fanson.ime`、0.4.2 / 21、minSdk31 / targetSdk35；最终 APK 唯一 uses-permission 为 VIBRATE。
- apksigner verify 验证通过，v2 签名有效；证书 SHA-256 `ab6097e0e833022396da5aa6d697e8b065dee781ff64ac16c586105e764d4ba2`，与既有真机安装相同。
- 已导出并人工查看本次模拟器截图：[句首自动大写图标与键帽](../screenshots/49-english-auto-capitalize-0.4.2.png)、[I'm 第一候选、im 第二候选](../screenshots/50-english-contraction-first-0.4.2.png)。均为合成试打内容，显示完整，无弹窗遮挡。

## 真机部署与待验收

已在操作前告知用户，即将覆盖安装供其验收。目标为当前连接的 HUAWEI NOH-AN00，原版本 0.4.1 / 20。仅备份公开代码 APK 到忽略目录 `.local/phone-before-english-0.4.2.apk`，没有读取/导出个人数据库、词频、正文、剪贴板或真机截图。原包 SHA-256 为 `05dd49b35cc0db61750a452196f17b1331ad8fe04ba4c8714816bee424ea9b90`。

```powershell
# PhoneSerial 为本次已确认的连接设备；不把实际序列号写入仓库。
adb -s $PhoneSerial install -r dist/FansonIME-0.4.2-debug.apk
adb -s $PhoneSerial shell dumpsys package com.fanson.ime
adb -s $PhoneSerial shell am start -W -n com.fanson.ime/.settings.MainActivity
```

覆盖安装返回 Success，确认版本 0.4.2 / 21；首次安装时间保持 `2026-11-08 13:46:19`，更新时间为 `2026-11-11 21:42:29`，首页启动 Status ok。没有卸载、清库、切换手机默认输入法、修改手机设置或注入测试正文；没有把模拟器脚本用于手机。

首次包原计划验收内容（用户随后报告 L 裁切，现已进入同版本修订）：句首/换行、点 Shift 取消后退格、连续输入、光标与选区、缩写候选/原词、L 长按以及关闭拼写/自动大写的组合。安装成功不等于真机验收通过，当前没有提交授权。

## 限制与未验证项

句首以平台/宿主结果为准，Dr. 等缩写、非英文标点及不同宿主可能有差异，不做整句语法推断；返回 0 时保守小写。静态表不做英文学习。同步边界查询无定时任务或逐字 SQL，但没有新增端到端耗时、电量或长时间真机基准，不能据本次结果承诺所有宿主的性能。未验证 release 构建、横屏新图标在全部设备上的表现、跨型号及完整提交失败回滚。详细已知边界见 [待改进事项](../technical/KNOWN_GAPS.md)。

## 同版本修订：L、数字行与标点长按

2026-11-11 用户报告首次包真机 L 半字裁切并提出追加需求。保持 **0.4.2 / 21**；本次电脑端验证完成，修订包已覆盖安装真机，**等待用户验收，未提交或推送**。上文 138 JVM/138 仪器与旧包 SHA 为首次包历史结果，不能代替本次修订。

### 实现与范围

L/l 改为单行原字母＋右上角灰色撇号，不挪动主字母基线；三语数字行长按采用半角 !@#$%^&*()，数字短按不变。数字行开启时，逗号/句号长按分别为中文全角冒号/成对引号或英语韩语半角冒号/直双引号；关闭数字行时恢复原全角/半角叹号问号。中文引号在原会话校验与成功提交回调后，核对光标前一个右引号再内移。无匹配、失败及搜索不移动宿主光标。

无新数据表/设置键、依赖、权限、外部资源或许可变化；现有数字页、符号页和原输入顺序保持。详细布局、UTF-16 坐标和失败边界见 [长按维护](../technical/KEYBOARD_SHORTCUTS.md) 与 [修订方案](../technical/proposals/2026-11-11-keyboard-long-press.md)。

### 本次实际执行和结果

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/start_test_emulator.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_debug.ps1 -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test_emulator.ps1 -Serial emulator-5580 -Focus keyboard_shortcuts
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_debug.ps1 -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test_emulator.ps1 -Serial emulator-5580
```

- 本次构建包含资源、Debug、androidTest、JVM 及 Lint，最终 BUILD SUCCESSFUL。**14 个 JVM 文件、143 项测试，0 failures/errors/skipped；Lint XML 0 个问题**。新增 KeyboardShortcutsTest 3 项、PairedQuoteCursorTest 2 项。
- 专项返回 **42 个唯一 passed 标记、CODE -1**，包含基础准备和七组新增检查；专项通过后补齐自定义 Button 的工具构造入口，消除 ViewConstructor Lint 警告。不是用缩小主字母或提高键高规避裁切。
- 最终完整回归在该构造修正后重新执行，**145 个唯一 passed 标记、count=145、INSTRUMENTATION_CODE: -1，无 failure/Process crashed，脚本 exit 0**。包含原 138 项及新增七组；没有复用上一包的通过记录。
- 七组覆盖：40dp/36dp 键高宽、字体 1.0/1.3 倍、深浅主题下 L/l 与普通按钮的相同基线/单行/无省略及高度边界；三语十数字短长按精确映射；三语真实 1/0 长按松手不追加数字；三语未确认候选/预编辑长按数字先确认；数字行开关两态的三语标点与短按保持；中文引号在空文本/正文中间/选区/代理对前文/已有候选后的光标位置及继续五笔输入、无匹配保护；数字和引号在 emoji 搜索只改关键词、保留宿主正文与光标。
- 原 !/? 历史回归显式关闭数字行；新增检查后恢复 48dp/数字行开启/五笔，整套最后恢复英语自动大写与数字行默认 true。测试全部使用合成数据，仅指定隔离 API31 `emulator-5580`，没有运行手机测试脚本；完成后关闭本次启动的模拟器进程，保留 AVD。
- 日志：`.local/shortcuts-build-final-0.4.2.log`、`.local/shortcuts-focused-0.4.2.log`、`.local/shortcuts-full-0.4.2.log`。本轮从 Python 以参数数组调用 PowerShell 并分别记录 stdout/stderr 与真实退出码；图形驱动的 s_glBindAttribLocation 输出没有被误当成失败，也没有隐藏仪器失败。
- 同步检查资源、Markdown 本地链接、git diff --check、未暂存/未提交状态以及 APK 版本、权限、签名；不将这些静态检查冒写为额外跑了一次全套测试。

### 当前修订包与截图

- 该次历史修订包：`.local/FansonIME-0.4.2-before-chinese-adjustment.apk`（dist 同名路径用于最新包），**10,405,336 字节**；与本轮 Gradle 输出一致，旁有 sha256 文件。
- SHA-256：`085c3ac86577f7fc137769a3950a5b77d0aa2a59a81b76fa037e3ca13c1555a5`。
- aapt 核对 `com.fanson.ime`、0.4.2 / 21、minSdk31 / targetSdk35；最终 APK 唯一权限仍 VIBRATE。apksigner 验证 v2 有效，证书 SHA-256 仍为 `ab6097e0e833022396da5aa6d697e8b065dee781ff64ac16c586105e764d4ba2`。
- 已导出并查看本轮完整回归截图：[40dp L 与右上角数字提示](../screenshots/51-corner-l-number-hints-0.4.2.png)、[中文成对引号与中间光标](../screenshots/52-chinese-quote-pair-number-row-0.4.2.png)。当前键盘主字母完整，没有双行 L 或弹窗遮挡；试打均为合成内容。前文 49/50 保留首次包的历史截图。

### 真机部署与待验收

操作前已告知将覆盖安装修订包。HUAWEI NOH-AN00 原安装为首次 0.4.2 / 21；只备份公开代码 APK 到 `.local/phone-before-shortcuts-0.4.2.apk`，未读取/导出个人数据、正文、剪贴板或真机截图。

```powershell
# PhoneSerial 为已核对的当前连接设备，不写真实序列号到仓库。
adb -s $PhoneSerial install -r dist/FansonIME-0.4.2-debug.apk
adb -s $PhoneSerial shell dumpsys package com.fanson.ime
adb -s $PhoneSerial shell am start -W -n com.fanson.ime/.settings.MainActivity
```

安装返回 Success；版本仍 0.4.2 / 21，首次安装时间保持 `2026-11-08 13:46:19`，更新时间变为 `2026-11-11 22:04:59`，首页启动 Status ok。另只读导出安装后的公开代码 APK，SHA-256 与当前修订包完全相同，确认手机收到本次同版本修订。没有卸载、清库、更换应用 ID、切换默认输入法、修改手机设置或注入测试正文。

**等待用户重新真机验收**，重点为原 L 裁切设备、数字行开关两态、三语数字长按，以及中文引号内继续输入。覆盖安装成功不等于验收通过，仍未获得本版提交授权。

### 未验证项

不同宿主可能拒绝 getSurroundingText/setSelection；无法核对右引号或位置时保守停在引号后，不猜测移动。模拟器对 1.0/1.3 字体及最小键高的结果不能替代所有厂商字体/极端比例、release/全部横屏或真实输入体验。未新增端到端延迟、电量或长时基准。规则不增加定时任务/逐键 SQL，继续遵守 Apache-2.0 与各第三方独立许可。

## 中文标点再调整：全角与简繁键引号

用户明确反馈此前其他功能测试通过，本次仅调整中文数字 1/9/0 全角、句号长按恢复问号、成对引号改为简/繁角标长按；简/繁原位置和短按切换保持，英语/韩语不变。保持 **0.4.2 / 21**。本次电脑端验证完成并已覆盖安装，**用户于 2026-11-11 确认最终真机验收通过并授权提交，未授权推送**。上文 143 JVM/145 完整回归及历史 SHA 属于此前包，不是本轮重新运行的结果。

### 本轮实际检查

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/start_test_emulator.ps1
adb -s emulator-5580 shell getprop sys.boot_completed
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_debug.ps1 -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test_emulator.ps1 -Serial emulator-5580 -Focus keyboard_shortcuts
```

- 本次离线构建含 Debug、androidTest、资源、JVM 与 Lint，BUILD SUCCESSFUL；**14 个 JVM 文件、144 项测试，0 failures/errors/skipped；Lint XML 0 问题**。KeyboardShortcutsTest 新增中文全角例外测试（当前 4 项），同时检查其他数字及英语/韩语不变。
- 最终专项原始结果 **43 个唯一 passed 标记、count=43、INSTRUMENTATION_CODE: -1，无 failure/Process crashed，脚本 exit 0**，包含基础准备和当前八组长按检查。复测十数字短长按、三语真实 1/9/0 松手不追加数字、预编辑确认、数字行开关双态标点、引号各种插入位置、无匹配和搜索隔离。
- 新增简/繁专用组：数字行开/关、简/繁两态真实长按都成对输入引号且不触发原短按；短按仍切换简繁且保持正文/光标。角标布局对比扩展到 L/l/简/繁，覆盖 40dp、放大字体 1.3 与深浅主题的原基线、单行及无省略。中文句号长按问号不再移动光标。
- 因本次仅映射和按钮入口局部变更，用户已确认其他功能通过，本轮没有再重跑此前完整 145 项；不把专项 43 项称为完整回归。五笔 worker、英语/韩语主线程、成对引号成功提交和位置核对机制均未重构。
- 最初在模拟器尚未启动完成时调用脚本，被现有“not a ready Android emulator”门控拒绝，未执行测试；等 sys.boot_completed=1 后才启动。首轮就绪后的专项在测试清空正文后批量发送数字处超时，后将测试 clear 复用现有 refresh 建立清空后的编辑会话，再执行批量输入；断言未放宽、生产输入链路未修改，随后专项通过。失败日志保留，不冒写为通过。
- 最终日志：`.local/chinese-shortcuts-build-r2-0.4.2.log`、`.local/chinese-shortcuts-focused-r2-0.4.2.log`；早期记录在 `.local/chinese-shortcuts-focused-0.4.2.log` 和 `.local/chinese-shortcuts-focused-ready-0.4.2.log`。只在隔离 `emulator-5580` 使用合成数据，保留既有 AVD，完成后停止本次模拟器进程。
- 已导出并查看本轮截图：[中文全角数字提示、句号问号与简键引号角标](../screenshots/53-chinese-toggle-quote-hint-0.4.2.png)。简字保持单行原位，无裁切，试打光标在成对引号中间。旧截图 51/52 属于上一映射包的历史证据。

### 当前包与真机部署

- 当前 APK：`dist/FansonIME-0.4.2-debug.apk`，**10,405,552 字节**；SHA-256 `2a76e8c28a393bf45c8ebb340cfa03ae9f312c5bcf683b9a8749f23727f07d6f`，旁有 sha256 文件，与本次 Gradle 输出相同。
- aapt 核对 `com.fanson.ime`、0.4.2 / 21、minSdk31 / targetSdk35；仍只有 VIBRATE 权限。apksigner v2 验证通过，证书 SHA-256 `ab6097e0e833022396da5aa6d697e8b065dee781ff64ac16c586105e764d4ba2` 保持。
- 操作前已告知用户将覆盖安装供复验；先只备份公开代码 APK 到 `.local/phone-before-chinese-adjustment-0.4.2.apk`，旧包 SHA 为 `085c3ac86577f7fc137769a3950a5b77d0aa2a59a81b76fa037e3ca13c1555a5`，未读取个人数据。使用 `adb -s $PhoneSerial install -r dist/FansonIME-0.4.2-debug.apk` 返回 Success，版本仍 0.4.2 / 21。
- 首次安装时间保持 `2026-11-08 13:46:19`，更新时间变为 `2026-11-11 22:22:05`；MainActivity 启动 Status ok。只读导出安装后的公开 APK 核对 SHA 与本轮完全一致，确认收到本次同版本修改。
- 没有卸载/清库、修改手机设置/默认输入法、注入测试正文、读取私有数据库或手机截图。用户之前“其他功能测试通过”按原范围保留，当前三项改动已于 2026-11-11 获用户验证通过和提交授权；验收依据为用户明确反馈，而非仅覆盖安装成功。

稳定规则见 [长按维护](../technical/KEYBOARD_SHORTCUTS.md)。无版本/数据库升级、权限、依赖或第三方内容变化，Apache-2.0 与既有第三方许可保持。不同宿主读取周边/移动光标限制、极端字体、release 和电量/长时基准仍未由本轮验证覆盖。

## 最终验收与本轮提交前检查

2026-11-11，用户在真机复验后明确反馈“验证通过，可以提交代码了”，据此确认最终 0.4.2 / 21 验收通过并授权 Git 提交，未授权推送。保留前面各包的真实测试与反馈过程，不把初次包或中间包追记为已通过最终验收。

本轮仅同步验收状态及检查提交范围，没有修改运行代码、测试脚本或版本，也没有重新执行 144 JVM、43 项专项或此前 145 项完整回归。提交前重新执行资源校验、Markdown 本地链接及 Git 差异检查，复核既有测试报告与当前 APK 哈希；只暂存本次源码、测试、维护文档和合成截图，不包含本机配置、密钥、私有数据、日志或构建产物。提交标识以 Git 历史为准。
