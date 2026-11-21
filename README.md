# Chunksmith LOD Follow

Automatically schedules Chunksmith generation around the single-player host.

## Installation

Java 21, Minecraft 1.21.1, and NeoForge. Tested with 21.1.265. Download `chunksmith_lod_follow-1.2.0.jar` from Releases. Use Chunksmith 4.9.0 for NeoForge 1.21.1 and a working Voxy renderer. The build was tested with Roxy 0.4.2 and Voxy 0.2.16-beta. The radius is 1,024 chunks in the current dimension. Chunksmith controls generation performance. Dedicated servers are outside this mod's scope.

单人世界中围绕玩家自动安排 Chunksmith 远景生成，随移动及维度切换更新。

## Build

Use JDK 21 and Python 3.11+. Create a text file with one absolute JAR path per line for the mapped Minecraft 1.21.1 and NeoForge 21.1.265 libraries, plus `chunksmith-4.9.0+1.21.1-neoforge.jar`.

```sh
python build.py --classpath-file classpath.txt --output chunksmith_lod_follow-1.2.0.jar
```

## License and feedback

MIT. Chunksmith is an independent GPL-3.0-only mod by Kishku7. Obtain it from its own project.

Report issues here with reproduction steps and your Minecraft, NeoForge and mod versions.
