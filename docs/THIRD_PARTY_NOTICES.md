# 第三方数据与构建工具

## Rime 五笔 86 码表

- 上游：https://github.com/rime/rime-wubi
- 固定 revision：`152a0d3f3efe40cae216d1e3b338242446848d07`
- 来源文件：`third_party/wubi86.dict.yaml`
- 原始作者与修改者见文件头及 `third_party/RIME-AUTHORS.txt`。
- 上游标注许可：LGPL-3.0，完整文本见 `third_party/RIME-LGPL-3.0.txt`，并附 `third_party/GPL-3.0.txt`。
- 本项目的转换：读取原文件的数据段，保留 a-y 的 1～4 码记录；按编码与文本去重；保留词频与 stem；生成 SQLite 查询索引。原始源文件保持原样。
- 原始数据、转换脚本和生成资源均保存在本项目，便于重建或替换码表。许可和作者说明同时随 APK 打包，并可从“关于”查看。

## Unicode emoji / CLDR

- Emoji 16.0：https://www.unicode.org/Public/emoji/16.0/emoji-test.txt
- CLDR 46：https://github.com/unicode-org/cldr
- CLDR 固定 revision：`e1d37acce5dae468c414172be53b666d58d45be8`
- 许可：`third_party/UNICODE-LICENSE.txt`；同时保留原始文件内说明。
- 转换：选取 fully-qualified emoji 序列，合并中文名称与搜索关键词，区分肤色变体，生成 JSON。面板使用系统字体，不内置第三方 emoji 图像。

## 简繁映射：OpenCC 1.1.9

- 来源：[BYVoid/OpenCC](https://github.com/BYVoid/OpenCC)，revision `556ed22496d650bd0b13b6c163be9814637970ae`。
- 只使用 `STCharacters.txt` 与 `STPhrases.txt` 数据，不嵌入其 C++ 运行库。原文件保留在 `third_party/`。
- Apache-2.0；作者和许可同时保留在项目及 APK 的 `OPENCC-AUTHORS.txt`、`OPENCC-LICENSE.txt`。
- 转换：合并字符及词组映射，移除注释，稳定排序键并保留全部映射值，生成 `traditional.tsv`；手机端独立实现词组优先的最长匹配。

## 离线英文：FrequencyWords 2018

- 作者：Hermit Dave 与贡献者；源自 OpenSubtitles 2018 / OPUS。
- [FrequencyWords](https://github.com/hermitdave/FrequencyWords)，revision `525f9b560de45753a5ea01069454e72e9aa541c6`，`content/2018/en/en_50k.txt`。
- 上游明确将内容许可为 **CC BY-SA 4.0**（代码的 MIT 不用于声明数据许可）。原始文件、README 和完整许可均保留。
- 转换：保留 1～32 字母的小写 ASCII 单词，去重，按原始词频降序及词文字稳定排序，移除计数字段，生成 46,717 词的 `english.txt`。该派生数据继续按 CC BY-SA 4.0 提供。
- APK“第三方数据与许可”提供署名、来源、修改说明和完整许可；项目保留原始数据和可离线重建脚本 `tools/prepare_completion_data.py`。不暗示原作者认可本项目。

## Gradle wrapper

标准 Gradle wrapper 脚本及 JAR，Gradle 8.9。脚本保留上游版权与许可头。Gradle 项目：https://github.com/gradle/gradle，许可：https://www.apache.org/licenses/LICENSE-2.0 。

每个下载源的 URL 和 SHA-256 记录在 `resources.lock.json`。更新第三方数据时需同步更新本文件和应用内说明。
