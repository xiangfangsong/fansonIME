# 0.5.0 验证记录

日期：2026-11-12；版本 **0.5.0 / 22**；基于已提交的 0.4.2。电脑端验证完成，已覆盖安装真机，用户于 2026-11-12 明确确认“验证通过，提交代码”；本次按授权提交，未授权推送。历史验收不代替本次验证。

## 变更与边界

新增纯离线“全拼26键”和五笔长按 Z 临时拼音，正式/临时共用引擎、数据及候选；保留字词、音节边界、v/ü、简繁、原始拼音回车、无匹配保护和搜索隔离。临时两向清未提交编码，选词后保持，语言/输入框/隐藏重置；FIFO 屏障不丢已有正文提交。没有整句/简拼/纠错/拼音学习，未扩五笔数据，无权限/运行时依赖/个人库迁移。

方案及完整限制见 [实施方案](../technical/proposals/2026-11-12-pinyin.md)、[维护说明](../technical/PINYIN_INPUT.md)、[第三方声明](../THIRD_PARTY_NOTICES.md)。

## 已执行的电脑检查

在仓库根目录使用 Windows PowerShell、JDK 17、缓存的 Android SDK/Gradle 依赖，执行：

```powershell
python tools/prepare_pinyin_data.py
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_debug.ps1 -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/start_test_emulator.ps1
adb -s emulator-5580 shell getprop sys.boot_completed
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test_emulator.ps1 -Serial emulator-5580 -Focus pinyin
```

- 最终数据生成与资源校验通过：299,392 条规范记录，另有 24,814 条按音节生成的兼容拼写索引，共 324,206 条；43,328 个不同单字、250,038 个不同词组（含 38 个可靠整词读音的 THUOCL IT 补词），64,895 条去重后的短前缀记录。数据库 49,008,640 字节；保存原始压缩源及解压原始 SHA-256，完整许可/署名/修改说明随 APK 提供。
- 本次 JVM **153 项、0 失败/错误**，其中 PinyinEngineTest 9 项。英文/韩语原线程边界保持；字词、无匹配、回车、退格、分隔、v/ü、简繁、长编码及清空均用合成词典验证。
- assembleDebug、assembleDebugAndroidTest 通过，Lint **No issues found**。现有平台 API 弃用编译警告保留，不把它描述为零编译警告。
- 第一轮专项 **44 项通过**，包含 35 项准备/基础检查和 9 组拼音检查。正式/临时字词选择、真实 Z/L 长按及松手、不匹配保留/清码、选词后保持、生命周期/语言重置、emoji 搜索目标和已有关键词保留、排队第五码正文提交与连续六次切换通过。
- 后续补默认全拼/地球回切、字根开启/数字行隐藏/深浅主题、拼音不写五笔学习，完整回归 **159 项通过**。最终音节拼写修正后，最终包专项 **48 项通过**（35 项准备/基础检查＋13 组拼音检查），覆盖 `nver/nv'er` 的“女儿”、`nueren/nveren` 的“虐人”和两种 lue/lve 拼写。

所有仪器脚本只运行明确指定的隔离 **emulator-5580 / Android 12 API31 x86_64**，保留现有 `fansonIME_API31` AVD。目标模拟器关闭 Wi-Fi/移动数据。未使用真机运行测试脚本，未读取真机个人数据。

验证及截图导出后执行 `adb -s emulator-5580 emu kill`，返回 OK；只关闭本轮启动的隔离模拟器，保留 AVD 数据目录。

## 完整回归与修正

执行 `tools/test_emulator.ps1 -Serial emulator-5580`。首次在新增拼音检查全部通过后，旧五笔词频检查断言初始学习为空失败：新增 FIFO 用例用合成五笔短语触发了一次真实五笔学习，清理短语时未同时清理共享学习 fixture。修正测试隔离，在拼音用例结束经现有 WubiFrequencyStore 清理合成学习，并增加“正式/临时拼音不会新增五笔学习”的断言；没有通过修改产品词频规则绕过断言。随后重新构建测试 APK 并重跑完整回归，**159 项、INSTRUMENTATION_CODE: -1，无 failure**。

完整回归主包 SHA-256 为 `2af39ad455c7a44d1d7416a5461e7f8ee27eef2002437d136d5eed86d3acc9a8`。其后复查发现整串替换 nve→nue 会误改“女儿”nver，改为生成阶段按音节展开兼容拼写，运行时只规范声调/ü；前缀索引按词去重，普通无分隔输入跳过逐候选边界计算。另取消连续切换/会话结束时的旧模式 toast、更新试打区提示。最终包完成上述 **48 项专项**和 **153 JVM、Lint、资源与 APK 审计**；没有把此前 159 项说成在最终字节包上重新执行。

修正词库说明时，资源校验曾拦截一份尚未同步到 assets 的旧 PINYIN-NOTICE；同步后重新校验、构建通过。生成器已统一复制该说明，后续离线重建也核对原文相等。

最后执行 `gradlew.bat :app:clean --offline --console=plain`，再执行 `tools/build_debug.ps1 -Offline`，78 个任务全部重新执行并通过（153 JVM、零失败/错误，Lint 无问题）。最终包专项使用此重建产物。初次增量产物曾为 52,699,848 字节，清理构建后为下列 32,088,686 字节；不分发前者。

## 资源与产物核对

通过 Python 对照当前 Git HEAD 字节核对，五笔库、读音库、英语词表、简繁 TSV、emoji JSON 完全不变。五笔 DB SHA-256 仍为 `a42d340cb0e456ba96a348606778da443743abee13b536f80cd19af827925894`。

最终版本再次执行离线拼音生成器，重建字节完全一致，`pinyin.db` SHA-256：`93b742f7b2d43c92cb40ec3a3aab315a8126795b4598cccc905932b49f4fdefd`。实际查询确认“微信”“支付宝”“小程序”“二维码”首选，“西安”边界分隔、lv/lve/lue 及女儿/虐人兼容拼写可用，原五笔不借此扩词。

通过本地临时审计 `python .local/audit_pinyin_artifacts.py` 核对 Git HEAD 原资源、ZIP 内资源/许可及离线重建；最终 clean 包使用 `--check-only` 再核对，逐项比对 APK 内 `data_info.json` 所有资源哈希及全部许可原文，未包含 native `.so`。用 SDK 36.0.0 的 `aapt dump badging` 核对应用 ID `com.fanson.ime`、0.5.0 / 22、minSdk31/targetSdk35，唯一权限 VIBRATE；`apksigner verify --verbose --print-certs` 验证 v2 有效，证书 SHA-256 保持 `ab6097e0e833022396da5aa6d697e8b065dee781ff64ac16c586105e764d4ba2`。临时审计脚本不作为共享构建依赖；正式资源检查入口仍是 `tools/verify_assets.py`。

最终归档：`dist/FansonIME-0.5.0-debug.apk`，**32,088,686 字节**；SHA-256 **`70a1c77c80b4c66e0fbafbe1308d1867e3a30753df119ba355bbde60348762ba`**，与 Gradle 输出相同，旁有 `.sha256` 文件。`git diff --check` 通过，17 个新增/变更 Markdown 的本地文件链接目标检查通过；无私有配置、密钥或 APK 加入暂存。

## 查询测量与画面

最终 API31 Debug 模拟器用 8 种合成拼音循环查询 120 次，P50 **0.4575ms**、P95 **4.2426ms**、最大 **6.3548ms**。只测仓库查询，不含 UI/宿主编辑、首次复制或端到端耗时，也不推断真机表现；JSON 位于该隔离模拟器 `externalCacheDir/qa/pinyin-query-0.5.0.json`。

人工查看并归档 [正式全拼](../screenshots/57-pinyin-modern-word-0.5.0.png)、[临时拼音浅色字根](../screenshots/59-temporary-roots-light-0.5.0.png)、[临时拼音深色字根](../screenshots/59-temporary-roots-dark-0.5.0.png)：正式普通字母/语言标签、Wubi 原键帽/标签保留、Z 深色图标和选中底色、分隔符与 toast 正确，未见裁切。所有画面为合成模拟器输入。

## 真机覆盖安装（2026-11-12）

用户明确要求安装，操作前已告知覆盖安装并保留个人数据。目标为已连接的 HUAWEI NOH-AN00 / API31，原安装 0.4.2 / 21。仅备份公开代码 APK 到忽略目录 `.local/phone-before-pinyin-0.5.0.apk`，原 SHA-256 为 `d4cb45fa0892503e9afa3e1336a43ad60710bf28e6b060a804bf906fc1851e92`；新旧包经 apksigner 核对签名一致。

执行：

```powershell
adb -s PQY0221206001213 install -r dist/FansonIME-0.5.0-debug.apk
adb -s PQY0221206001213 shell dumpsys package com.fanson.ime
adb -s PQY0221206001213 shell am start -W -n com.fanson.ime/.settings.MainActivity
```

安装返回 Success，版本为 0.5.0 / 22；首次安装时间保持 `2026-11-08 13:46:19`，更新时间为 `2026-11-12 23:12:00`，设置页面启动 Status ok。安装后再次读取公开 APK 到 `.local/phone-after-pinyin-0.5.0.apk`，SHA-256 与最终包 `70a1c77c80b4c66e0fbafbe1308d1867e3a30753df119ba355bbde60348762ba` 完全相同。

没有卸载、清库、改应用 ID、切默认输入法、改手机设置或注入测试正文；未读取个人数据库、词频、正文、剪贴板或真机截图。覆盖安装保留既有应用数据，实际输入效果等待用户验收，未提交或推送。

## 用户验收与提交授权（2026-11-12）

用户在上述最终包覆盖安装后明确确认“验证通过，提交代码”。本版用户真机验收通过，按授权提交代码、固定源数据、完整许可和对应文档；不推送远端。此前各轮电脑验证范围与安装时状态保留，不扩大为所有宿主或长期性能均已验证。

提交检查包含 50 个文件，不含本机配置、密钥、个人数据或 APK。暂存后的完整 diff 检查报告上游 THUOCL README 一处行尾空白、万象 NOTICE 及其 APK 原文副本两处末尾空行；这些是锁定原文，自带空白保持不改，避免破坏原文和来源哈希。排除这三份原文后的暂存 diff 检查通过，原始源数据及 APK 许可副本的暂存字节另按哈希核对。

## 未验证项

本版已通过用户真机输入验收；未覆盖所有厂商/宿主、长时间耗电、所有专业词/读音或资源/宿主故障恢复。纯离线固定字词排序不等于商业整句输入体验。Debug 模拟器查询测量不能代表真机端到端延迟；已有 G01/G03/G05/G06/G10 等边界保留。未运行 release 验证、未改 IDE 工程名或模块配置。
