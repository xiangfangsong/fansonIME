# 个人 JSON 备份（0.5.1）

状态：已实现，用户已确认本版真机验收通过；当前验证与已知限制见 [0.5.1 记录](../validation/VALIDATION-0.5.1.md)。线程取舍见 [方案](proposals/2026-11-13-personal-backup.md)。

## 使用流程和覆盖语义

设置页“隐私与数据”提供“导出 JSON 备份”“导入 JSON 备份”。

- 导出先勾选四类内容，至少一项，再通过系统“另存为”选位置。未选类别不写入 JSON；已选但没有记录的类别写为空集合。
- 导入先选文件，后台完整解析校验后展示四个复选框与条数。缺失类别灰显、未勾选；存在的空集合显示“0 项”且仍可选。至少选择一个存在类别才能确认。
- 自定义短语按 `(code,text)` 合并去重，保留原短语。
- emoji 覆盖还原次数与最近使用顺序，恢复“常用”页的排序依据；不是独立收藏列表。不同 Android 字形支持可能影响实际显示项。
- 五笔词频覆盖还原来源身份、分数和更新时间，继续原衰减规则，已过期记录不会变为有效学习。来源仍由现有查询核对，不能凭文件生成内置候选。
- 设置覆盖文件中已识别且有效的白名单键；缺少的键、未勾选类别保留原值。设置页立即重建，键盘下次开始/显示输入时读取；当前语言在下次开始输入会话读取。
- 明确选择空 emoji / 词频类别会清空对应记录，选择框说明此行为。取消选择器或确认框不导入。

## 格式和兼容边界

`PersonalBackupJson` 使用平台 JsonReader/JsonWriter 流式读写 UTF-8。根字段 `format="FansonIME"`、整数 `version=1`；可选 `phrases`、`emojiUsage`、`wubiFrequency` 数组与 `settings` 对象。未来版本拒绝，未知字段有界跳过，未知设置键忽略。已识别字段损坏使整个文件拒绝，不当作缺失或部分导入。

```json
{
  "format": "FansonIME",
  "version": 1,
  "phrases": [{"code": "abc", "text": "合成示例"}],
  "emojiUsage": [{"text": "😀", "count": 3, "lastUsed": 7}],
  "wubiFrequency": [{"inputCode": "a", "sourceCode": "aaaa", "sourceText": "合成词", "text": "合成词", "score": 2.0, "updatedAt": 1800000000000, "custom": false}],
  "settings": {"theme": "system", "number_row": "true"}
}
```

不导出剪贴板、试打正文、内置词库、账号或宿主信息。简繁、英文拼写和临时拼音没有持久设置，本版不将它们新增为设置。

当前限制：文件最多 64 MiB，最多 20,000 条短语、10,000 条 emoji、20,000 条词频。短语/词频文字最多 200 个 Kotlin UTF-16 单元，编码 `[a-y]{1,4}`；来源码以输入码开头，分数有限 `(0,16]`，时间非负，custom 为布尔。emoji 文本最多 128 单元，次数/最近顺序是 `1..10^12` 整数，避免恶意极值使后续累加溢出。重复字段/记录身份、非 UTF-8、尾随数据、过深未知内容拒绝。对象字段最多 100，未知内容递归深度小于 16。不会保证兼容其他输入法的 JSON。

`BackupPreferences.defaults/valid` 维护 15 个白名单键：三项语言、英语句首、回车规则、五笔学习、四码唯一、第五码、震动开关/强度、字根、数字行、符号返回、主题、键高。导出补齐当前默认值，导入检查枚举、布尔和数值范围。新增持久偏好时同步此表及往返测试，不能全表导出。

## 数据、线程和生命周期

`BackupController` 管选择、系统文件选择器与反馈；`LocalStore` 管分类快照与事务；`WubiFrequencyStore` 协调共享模型。未向服务按键入口增加业务。

导出在共享词频磁盘队列获取 `model.snapshot()`，包含未隐藏保存的学习，不清 dirty、不触发日常保存；其他类别在同一 SQLite 事务读取。JSON 和文件访问在独立文件队列。快照不承诺与同时发生的输入/emoji 操作处于同一全局时刻。

导入完整验证后，在共享磁盘队列对所选类别执行同一 SQLite 事务，无 schema 变化。词频 `beginRestore()` 提升 epoch 并暂拒反馈，不持模型锁执行事务；成功后 `finishRestore()` 清旧模型、载入已持久化记录并再升 epoch；回滚保留旧模型和 dirty，解除暂停，旧反馈仍失效。排队保存只读取还原后的模型，不复活旧记录。有限索引重建在同步区完成，属于显式还原操作；正常输入 worker、宿主编辑和 session 协议不改。

控制器用 Activity 非配置实例保留，旋转释放旧 Dialog/Activity 引用并恢复选择/进度。个人内容不进 Bundle；进程死亡后的选择器结果提示重新开始。无断点续传、自动重试或后台定时备份。错误不含个人内容；写入失败可能留下不完整文件，提示重新导出，个人库导入失败无部分写入。

## 权限和隐私

最低 Android 12 / API31，使用 `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` 和 `CATEGORY_OPENABLE`，只读写返回的单文件 `content:` URI。不申请存储、所有文件、媒体、网络、相机权限、目录或长期 URI 授权；Manifest 仍仅 VIBRATE。见 [Android SAF 官方说明](https://developer.android.com/training/data-storage/shared/documents-files)。

系统授予所选文件访问权，应用没有全部存储访问权。JSON 为明文，可由用户选择的云文件提供方保存，输入法自身无网络权限。导出页说明内容与保管责任；原系统备份/迁移排除保持。无新依赖、第三方内容或日志。

## 验证入口

`BackupFrequencyTest` 验证快照、还原成功/失败和代数；`PersonalBackupChecks` 验证 JSON、事务、共享模型、界面和系统选择器。执行 `tools/test_emulator.ps1 -Serial emulator-5580 -Focus backup`，真实与模拟流程的范围见 [本版记录](../validation/VALIDATION-0.5.1.md)。
