# Linear 与 Blinear 的区别

[English](Linear-vs-BLinear.md)

**Blinear（Buffered Linear）是 Linear 的重新实现。** 它沿着 Linear 区域存储的思路，重新设计了数据缓冲、按需读取、并发访问和文件同步机制。本项目分别通过 `LinearRegionFile` 和 `BufferedLinearRegionFile` 支持这两种实现。

“重新实现”描述的是它们的实现关系；文件格式兼容性需要单独判断。当前 Blinear 有自己的主文件布局，不能因为它是 Linear 的重实现，就认为 `.linear` 和 `.b_linear` 可以直接互换。

本文以本仓库当前代码为准。其他服务器分支、历史版本的 Linear 或 Blinear 实现可能存在差异。

## 实现来源与支持的服务端

本项目的 **Linear 实现取自 Luminol，Blinear 实现取自 Lophine**，随后进行了 Regionerator 所需的适配与修改。这里的来源指本项目引入代码的直接上游。

以下列表根据 GitHub 上的格式枚举、实现代码或项目文档核实，核实日期为 **2026-11-14**。它列出已找到的支持实例，不是完整名单，也不代表已完成这些服务端与本插件的运行兼容性测试。证据链接固定到核实时的提交，支持范围以表中的分支为准。

| 服务端 | 核实分支 | 支持的格式 | 说明与证据 |
| --- | --- | --- | --- |
| Luminol（[社区存档](https://github.com/LuminolCustomArchive/Luminol)） | 存档 `dev/26.2` | Linear 与 Blinear | 本项目 Linear 实现的直接来源；项目已停止维护，原仓库已删除；存档的[格式枚举][luminol-formats]保留两种实现 |
| [Lophine](https://github.com/LophineLabs/Lophine) | `ver/26.3` | Blinear | 本项目 Blinear 实现的直接来源；[README][lophine-readme]明确列出 `b_linear`，并称其为 Linear 的重新实现 |
| [Leaf](https://github.com/Winds-Studio/Leaf) | `ver/1.21.11` | **Linear 与 Blinear** | [格式枚举][leaf-12111-formats]同时包含 `LINEAR_V2` 和 `B_LINEAR` |
| [Leaf](https://github.com/Winds-Studio/Leaf) | `ver/26.3` | Blinear（可选格式） | 该分支的[格式枚举][leaf-263-formats]保留 `B_LINEAR`，已移除独立的 Linear 选项；不能将 1.21.11 的双格式支持套用到所有后续版本 |
| [Arbor](https://github.com/ArborTeam/Arbor) | `ver/26.1.2` | **Linear 与 Blinear** | Luminol 的社区分支；[格式枚举][arbor-formats]同时注册两种实现 |
| [DivineMC](https://github.com/BX-Team/DivineMC) | `ver/26.3` | **Linear 与 Blinear** | [格式枚举][divinemc-formats]包含 `LINEAR` 和 `B_LINEAR`，分别使用 Linear 与 Buffered 后端 |
| [LinearPaper](https://github.com/RealTriassic/LinearPaper) | `ver/1.21.1` | Linear | [README][linearpaper-readme]包含 Linear 配置说明；项目明确宣布停止维护，仓库已归档 |
| [Kaiiju](https://github.com/KaiijuMC/Kaiiju) | `ver/1.20.1` | Linear | [Linear 补丁][kaiiju-linear]包含 `.linear` 读写实现，写入版本为 2；仓库已归档 |

使用 Leaf 等支持多种格式的服务端时，应根据服务器实际配置的区域存储格式选择 Regionerator 对应的实现。服务端同时支持两种格式，不代表两种文件可以直接互换；具体配置方式与可用格式版本以所用服务端版本为准。

## 核心区别

| 比较项 | Linear | Blinear |
| --- | --- | --- |
| 本项目的实现类 | `LinearRegionFile` | `BufferedLinearRegionFile` |
| 实现关系 | 本文比较的 Linear 实现 | **Linear 的重新实现** |
| 区域范围 | 每个区域包含 32 × 32 个区块 | 同样包含 32 × 32 个区块 |
| 本项目识别的文件名 | `r.x.z.linear` | `r.x.z.b_linear` |
| 当前写入格式 | Linear v3 | Blinear v3，两者布局不同 |
| 数据读取 | 先读取整个区域文件；v3 桶数据按需解压 | 当前 v3 主文件按桶按需读取，加载后的区块数据进入临时交换文件 |
| 区块数据缓冲 | 主要保存在 Java 堆内存中的字节数组里 | 主要保存在磁盘 `.swp` 文件里，内存保存索引、状态和临时缓冲 |
| 文件更新 | 重写完整区域文件到 `.tmp`，再替换原文件 | 通常追加修改过的桶并更新位置表；需要压缩整理时重写主文件 |
| 并发控制 | 关键操作使用 `synchronized` | 使用分层锁、每桶加载锁和修改版本计数 |
| 同步管理 | 通过 `flush()` 写入文件 | 提供后台同步管理器，关闭时也会执行必要的主文件同步 |

## Linear：以内存缓冲为主

当前 Linear 实现打开区域时，会将整个文件读入内存。对于 v3 文件，它先保留压缩后的桶数据，在需要查询或修改桶内区块时再解压。

已展开的区块数据会使用 LZ4 压缩，并保存在内存字节数组中。写入区域文件时，再将相关区块组织成桶，以 Zstd 压缩写入磁盘。未展开且未修改的桶可以直接复用已有压缩数据。

即使只修改少量区块，`flush()` 仍会生成完整的临时区域文件，再替换原文件。因此，复用未修改桶可以减少重新压缩的工作，但仍需要写出完整区域文件。

这种方式的数据流较直接，代价是区域文件内容和展开后的区块缓冲会占用堆内存，少量修改也可能产生较多磁盘写入。

## Blinear：重新设计缓冲与同步方式

**Blinear 对 Linear 的重实现，主要体现在存储引擎的读写路径上。** 当前实现把持久化主文件与临时交换文件分开管理：

- 主文件 `.b_linear` 保存持久化的区域数据，按桶使用 Zstd 压缩。
- 临时交换文件 `.b_linear.swp` 保存已加载或修改的区块数据，区块数据使用 LZ4 压缩。
- 内存保存区块位置索引、桶状态、修改版本以及读写时使用的临时缓冲。

在当前 v3 格式下，Blinear 可以按需从主文件读取桶，而不必先把整个主文件内容读进 Java 堆。它仍会使用内存和操作系统文件缓存，因此不能将其理解为“完全不占内存”。读取旧格式并转换时，也不一定具备相同的按需读取特性。

同步时，如果主文件已经具备有效的 v3 追加状态，Blinear 通常只追加修改过的桶，再更新桶的位置表。旧桶记录会成为无效空间，达到整理条件后，通过临时文件重写来回收空间。

这种设计可以减少部分更新场景下的整文件重写，但也引入了交换文件 I/O、索引管理、无效空间回收以及更复杂的并发控制。

## 文件格式与兼容性

本项目的 Linear 读取器支持 Linear 版本 1、2 和 3，写入版本为 3。Blinear 的读取逻辑包含对这些 Linear 文件以及旧 Blinear v2 文件的解析和转换支持，转换后的持久化数据使用 Blinear v3 布局。

需要区分以下几点：

- **Linear v3 和 Blinear v3 中的“v3”分别属于各自格式的版本编号。** 它们使用不同的文件标识和结构。
- Blinear 实现具有读取、转换 Linear 数据的代码路径；Linear 读取器没有对应的 Blinear 读取路径。
- 本插件的区域扫描分别匹配 `.linear` 和 `.b_linear` 后缀。解析器支持某种输入，不代表扫描器会自动发现并迁移另一种后缀的文件。
- 修改文件后缀不会执行格式转换。实际迁移还需要服务器或工具正确调用对应的读取和写入逻辑。

## 交换文件与持久化

Blinear 的 `.swp` 是临时工作文件。当前实现打开区域时会清理旧交换文件，交换文件也不会作为崩溃恢复日志重新读取。**持久化依赖主文件同步，不能把已写入 `.swp` 等同于已持久化到主文件。**

Blinear 更新主文件时，会先刷出追加的桶记录，再更新并刷出位置表；整理主文件时则先写临时文件再替换。Linear 同样会在临时文件写完后执行刷盘，再替换区域文件。这些机制用于控制落盘顺序，不能据此承诺任意中断情况下都不会损坏。

## 在 Regionerator 中的意义

Regionerator 通过统一的 `RegionInfo`、`ChunkInfo` 接口检查和删除区块，由各自后端完成实际文件操作。选择哪种实现，应与服务器使用的区域存储实现一致。

本插件中的 Blinear 删除写入会在提交删除后加载所有桶，确认区域是否为空，再关闭文件并完成必要同步。因此，Blinear 具备按需读取和后台同步能力，并不意味着 Regionerator 的每次删除都只读取少量数据或只在后台落盘。

性能上，Blinear 的设计有利于降低长期保留区块数据时的堆内存压力，并减少部分更新场景中的主文件写入量；实际收益取决于区域大小、访问模式、磁盘性能和同步频率。本文没有提供基准测试数据，不作固定倍数的性能结论。

## 实现来源与参考

本项目的 Linear 实现取自已停止维护的 Luminol；Blinear 实现取自 [Lophine](https://github.com/LophineLabs/Lophine)，并经过本项目适配。各服务端的支持范围及版本见上表。相关来源与许可证说明见仓库 [README](README.md#licenses)。

- [LinearRegionFile](src/main/java/com/github/jikoo/regionerator/world/impl/linear/LinearRegionFile.java)
- [BufferedLinearRegionFile](src/main/java/com/github/jikoo/regionerator/world/impl/blinear/BufferedLinearRegionFile.java)
- [BufferedLinearRegionFileFlusher](src/main/java/com/github/jikoo/regionerator/world/impl/blinear/BufferedLinearRegionFileFlusher.java)
- [LinearWorld](src/main/java/com/github/jikoo/regionerator/world/impl/linear/LinearWorld.java)
- [BufferedLinearWorld](src/main/java/com/github/jikoo/regionerator/world/impl/blinear/BufferedLinearWorld.java)

[luminol-formats]: https://github.com/LuminolCustomArchive/Luminol/blob/38a6a1af58b478f7507d8c5b8a2884eee9859fcb/luminol-server/src/main/java/me/earthme/luminol/enums/EnumRegionFormat.java
[lophine-readme]: https://github.com/LophineLabs/Lophine/blob/6e7e3db32bb6c1ad686b5dc14e62e8e3ad017662/README.md
[leaf-12111-formats]: https://github.com/Winds-Studio/Leaf/blob/12f438e06b8a35d6584687565ec5eeacb373972c/leaf-server/src/main/java/me/earthme/luminol/enums/EnumRegionFormat.java
[leaf-263-formats]: https://github.com/Winds-Studio/Leaf/blob/3ab88076100da0cfd2a7fa20521069591a7356a2/leaf-server/src/main/java/me/earthme/luminol/enums/EnumRegionFormat.java
[arbor-formats]: https://github.com/ArborTeam/Arbor/blob/a34be2de7a92ce3883584475a8b0d6e6a638991c/arbor-server/src/main/java/zone/little/arbor/enums/EnumRegionFormat.java
[divinemc-formats]: https://github.com/BX-Team/DivineMC/blob/a02492aa29a22f98066fd2a7ac946f310b6f9ece/divinemc-server/src/main/java/org/bxteam/divinemc/region/EnumRegionFileExtension.java
[linearpaper-readme]: https://github.com/RealTriassic/LinearPaper/blob/23aab43b667d13cad040c41c40ac96774a50b00c/README.md
[kaiiju-linear]: https://github.com/KaiijuMC/Kaiiju/blob/c2b7aec8f7b418a39a2ec408e6411e6f752379da/patches/server/0004-Add-Linear-region-format.patch
