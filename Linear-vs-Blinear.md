# Linear vs. Blinear

[简体中文](Linear-vs-Blinear.zh-CN.md)

**Blinear (Buffered Linear) is a reimplementation of Linear.** It builds on Linear's approach to region storage while redesigning data buffering, on-demand reads, concurrent access, and file synchronization. This project supports the two implementations through `LinearRegionFile` and `BufferedLinearRegionFile`, respectively.

“Reimplementation” describes their relationship; file format compatibility is a separate concern. Blinear currently uses its own master file layout, so `.linear` and `.b_linear` files cannot be treated as interchangeable simply because Blinear reimplements Linear.

This document describes the current code in this repository. Other server forks and historical versions of Linear or Blinear may behave differently.

## Implementation sources and supporting servers

This project's **Linear implementation was taken from Luminol, and its Blinear implementation was taken from Lophine**. Both were subsequently adapted and modified for Regionerator. These are the direct upstream sources from which this project imported the code.

The following examples were verified against format enums, implementation code, or project documentation on GitHub on **2026-11-14**. This is not an exhaustive list, and inclusion does not mean runtime compatibility with this plugin has been tested. Evidence links are pinned to the commits checked, and support claims apply to the branches listed.

| Server | Branch checked | Supported formats | Notes and evidence |
| --- | --- | --- | --- |
| Luminol ([community archive](https://github.com/LuminolCustomArchive/Luminol)) | Archived `dev/26.2` | Linear and Blinear | The direct source of this project's Linear implementation; discontinued, with its original repository deleted; the archived [format enum][luminol-formats] retains both implementations |
| [Lophine](https://github.com/LophineLabs/Lophine) | `ver/26.3` | Blinear | The direct source of this project's Blinear implementation; its [README][lophine-readme] explicitly lists `b_linear` and describes it as a reimplementation of Linear |
| [Leaf](https://github.com/Winds-Studio/Leaf) | `ver/1.21.11` | **Linear and Blinear** | The [format enum][leaf-12111-formats] includes both `LINEAR_V2` and `B_LINEAR` |
| [Leaf](https://github.com/Winds-Studio/Leaf) | `ver/26.3` | Blinear (selectable format) | This branch's [format enum][leaf-263-formats] retains `B_LINEAR` and has removed the separate Linear option; dual-format support in 1.21.11 should not be assumed for every later version |
| [Arbor](https://github.com/ArborTeam/Arbor) | `ver/26.1.2` | **Linear and Blinear** | A community fork of Luminol; its [format enum][arbor-formats] registers both implementations |
| [DivineMC](https://github.com/BX-Team/DivineMC) | `ver/26.3` | **Linear and Blinear** | Its [format enum][divinemc-formats] includes `LINEAR` and `B_LINEAR`, using separate Linear and Buffered backends |
| [LinearPaper](https://github.com/RealTriassic/LinearPaper) | `ver/1.21.1` | Linear | Its [README][linearpaper-readme] documents Linear configuration; the project explicitly states that it is no longer maintained, and the repository is archived |
| [Kaiiju](https://github.com/KaiijuMC/Kaiiju) | `ver/1.20.1` | Linear | Its [Linear patch][kaiiju-linear] implements `.linear` reads and writes, writing version 2; the repository is archived |

When using a server with multiple storage formats, such as Leaf, select the Regionerator implementation that matches the region storage format actually configured on the server. Supporting both formats does not make their files interchangeable. Configuration details and available format versions depend on the server version in use.

## Key differences

| Aspect | Linear | Blinear |
| --- | --- | --- |
| Implementation class in this project | `LinearRegionFile` | `BufferedLinearRegionFile` |
| Relationship | The Linear implementation compared here | **A reimplementation of Linear** |
| Region size | 32 × 32 chunks per region | Also 32 × 32 chunks per region |
| File names recognized by this project | `r.x.z.linear` | `r.x.z.b_linear` |
| Current output format | Linear v3 | Blinear v3, with a different layout |
| Data reads | Reads the entire region file first; decompresses v3 buckets on demand | Reads current v3 master file buckets on demand and stores loaded chunk data in a temporary swap file |
| Chunk data buffering | Primarily byte arrays on the Java heap | Primarily a disk `.swp` file, with indexes, state, and temporary buffers in memory |
| File updates | Rewrites the complete region file to `.tmp`, then replaces the original | Usually appends modified buckets and updates the position table; rewrites the master file when compaction is needed |
| Concurrency control | Uses `synchronized` for key operations | Uses a lock hierarchy, per-bucket loading locks, and write epoch counters |
| Synchronization management | Writes the file through `flush()` | Provides a background synchronization manager and performs required master file synchronization on close |

## Linear: primarily memory buffering

The current Linear implementation reads the entire file into memory when opening a region. For v3 files, it initially retains compressed bucket data and decompresses a bucket when its chunks need to be queried or modified.

Chunk data from decompressed buckets is compressed with LZ4 and stored in memory as byte arrays. When writing a region file, chunks are grouped into buckets and compressed with Zstd for disk storage. Buckets that have not been decompressed or modified can reuse their existing compressed data directly.

Even when only a few chunks change, `flush()` produces a complete temporary region file and replaces the original. Reusing unchanged buckets reduces recompression work, but the complete region file still needs to be written.

This approach has a relatively direct data flow. Its tradeoffs are heap usage for file contents and chunk buffers, and potentially substantial disk writes even for small changes.

## Blinear: redesigned buffering and synchronization

**Blinear's reimplementation of Linear primarily changes the storage engine's read and write paths.** The current implementation manages a persistent master file separately from a temporary swap file:

- The `.b_linear` master file holds persistent region data, compressed with Zstd per bucket.
- The `.b_linear.swp` temporary swap file holds loaded or modified chunk data, compressed with LZ4 per chunk.
- Memory holds chunk location indexes, bucket state, write epochs, and temporary buffers used during reads and writes.

With the current v3 format, Blinear can read buckets from the master file on demand without first loading the entire master file into the Java heap. It still uses memory and the operating system's file cache, so it should not be understood as using no memory. Reading and converting older formats may also have different loading behavior.

During synchronization, if the master file already has valid v3 append state, Blinear usually appends only modified buckets and then updates the bucket position table. Superseded bucket records become unused space. Once compaction conditions are met, a rewrite through a temporary file reclaims that space.

This design can reduce full-file rewrites for some update patterns. It also introduces swap file I/O, index management, space reclamation, and more complex concurrency control.

## File formats and compatibility

This project's Linear reader supports Linear versions 1, 2, and 3, and writes version 3. Blinear includes parsing and conversion paths for those Linear files and older Blinear v2 files. Converted persistent data uses the Blinear v3 layout.

The following distinctions matter:

- **The “v3” in Linear v3 and Blinear v3 belongs to each format's own version sequence.** They use different file identifiers and structures.
- Blinear has code paths for reading and converting Linear data; the Linear reader has no corresponding Blinear reading path.
- This plugin's region scanners match `.linear` and `.b_linear` extensions separately. Parser support for an input format does not mean the scanner automatically discovers and migrates files with another extension.
- Changing a file extension does not convert its format. Migration requires a server or tool to invoke the appropriate reading and writing logic.

## Swap files and durability

Blinear's `.swp` file is temporary working storage. The current implementation removes an old swap file when opening a region and does not replay it as a crash recovery log. **Durability depends on synchronization to the master file; writing to `.swp` is not equivalent to persisting data in the master file.**

When updating the master file, Blinear flushes appended bucket records before updating and flushing the position table. During compaction, it writes a temporary file before replacing the master file. Linear also flushes its completed temporary file before replacing the region file. These mechanisms control the order of writes to storage; they do not establish a guarantee against corruption under every possible interruption.

## What this means for Regionerator

Regionerator checks and deletes chunks through the common `RegionInfo` and `ChunkInfo` interfaces. Each backend performs the actual file operations. The selected implementation should match the region storage implementation used by the server.

In this plugin, Blinear's deletion write path applies pending deletions, loads all buckets to determine whether the region is empty, and then closes the file with the required synchronization. On-demand reads and background synchronization therefore do not imply that every Regionerator deletion reads only a small amount of data or persists changes exclusively in the background.

Blinear's design can reduce heap pressure when retaining chunk data over time and reduce master file writes for some update patterns. Actual benefits depend on region size, access patterns, disk performance, and synchronization frequency. This document provides no benchmark data and makes no fixed performance multiplier claims.

## Sources and references

This project's Linear implementation was taken from the discontinued Luminol project; its Blinear implementation was taken from [Lophine](https://github.com/LophineLabs/Lophine). Both have been adapted for this project. See the table above for server support and version details, and the repository [README](README.md#licenses) for source and license information.

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
