# 第三方数据与构建工具

项目原创代码和文档采用根目录的 [Apache License 2.0](../LICENSE)。本页列出的第三方内容及其派生产物继续遵循各自许可；项目主许可证不替代其版权声明、署名、源码或数据提供及相同方式共享等适用义务。许可范围见 [项目 README](../README.md#许可证)。

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

## 五笔86字根卡片与专用字体

- 字根键位事实参考 [riku-ri/wubi-kb](https://github.com/riku-ri/wubi-kb)，revision `c65590f6a524fa7cb9459d2095be87d22bd839fc`，逐键来源及校验值见 `third_party/wubi_roots/keys.json`；原始提取表 25 键、258 项保留不变。2026-11-10 通过 `tools/wubi_root_corrections.json` 修正 B/L/O/P，当前 262 项；键位及笔形事实另参考 [五笔拆解查询](https://hantang.github.io/search-wubi/wiki/roots/)及其繁简字根图、[Benber 字根说明](https://wubi.benber.net/guide/roots)，未复制参考图或字体。
- 字形来自上游 font Release 的 `UMingCN-wubi-86-kb.ttf`，原始 SHA-256 为 `4b9d2a7a2473ff39e097565e8906875fbe1097deac7e0891c0b150244d71223a`。该字体以 CJK-Unifonts / AR PL UMing CN 为基础，为五笔特殊字根修改了部分字形。
- 字体按 **ARPHIC PUBLIC LICENSE** 提供。完整许可、原始版权与上游修改声明保存在 `third_party/wubi_roots/`，同时随 APK 打包并在“第三方数据与许可”展示。
- FansonIME 于 2026-11-08 仅保留所需字形并重命名为 **Fanson Wubi Roots**，当时字形轮廓不变；该历史声明与项目名旧拼写保留（见[命名约定](technical/NAMING.md)）。2026-11-10 补齐字形，并从 U+793B（礻）移除末尾右点派生独立的 U+E000，表示“礻去末点／衤去末两笔”；其余现有字形轮廓不变。本次修改声明追加到字体 name 表及 APK 的 `WUBI-ROOTS-NOTICE.txt`。派生字体随 APK 和项目提供，继续按原许可自由复制、修改、再分发，不提供担保。
- `tools/prepare_root_data.py --source-font PATH` 使用 FontTools 4.55.8 校验原始字体并重建 84,952 字节子集；源字体下载地址和校验值见 `resources.lock.json`。构建不会联网下载字体。专用字体仅用于卡片，不影响正文的系统字体。

## 离线候选读音

- 单字资料：[mozillazg/pinyin-data](https://github.com/mozillazg/pinyin-data)，revision `fe29ad32bdf352b31a02b9936e537f575432e9a6`，使用 `pinyin.txt`。Copyright (c) 2016 mozillazg，MIT。
- 词组资料：[mozillazg/phrase-pinyin-data](https://github.com/mozillazg/phrase-pinyin-data)，revision `4afa3226ce53c9138b939e83c3bb294dbe0da511`，使用 `pinyin.txt`。Copyright (c) 2017 mozillazg，MIT。
- 原始文件、README 和完整许可保存在 `third_party/pronunciation/`；单字资料的 Unihan 等来源、词组资料的初始来源见各上游 README。固定下载 URL 和 SHA-256 见 `resources.lock.json`。
- `tools/prepare_pronunciation_data.py` 离线生成 `pronunciation.db`：Unicode NFC、按原序稳定去重，保留全部已收录读音；使用现有 Apache-2.0 OpenCC 映射补充繁体词组查询键，原始整词记录优先，不增造读音。
- 最终含 44,440 单字、47,152 原始词组及 33,219 个繁体词组查询别名；覆盖报告在 `third_party/pronunciation/coverage.json`。未知读音不推断，词组未收录时仅提供明确标示的逐字参考。
- 原作者、来源、修改说明及两个项目的 MIT 全文同时随 APK 打包，可从“第三方数据与许可”查看。不暗示资料作者认可本项目。

## Gradle wrapper

标准 Gradle wrapper 脚本及 JAR，Gradle 8.9。脚本保留上游版权与许可头。Gradle 项目：https://github.com/gradle/gradle，许可：https://www.apache.org/licenses/LICENSE-2.0 。

每个下载源的 URL 和 SHA-256 记录在 `resources.lock.json`。更新第三方数据时需同步更新本文件和应用内说明。
