# Regionerator
A Bukkit plugin for gradually deleting unused area, allowing you to free up disk space and get rid of old builds or devastated land.

### This is a fork version for 1.20.6+ and Paper/Folia servers!  
### Changes on many classes! So won't support Spigot!!!
  
## Features compared to the original plugin
1. Linear & BLinear format support
2. Folia support
3. Can configure interaction times and inhabitedtime checks to the region  
   (If you don't know what inhabited time is, see: https://minecraft.wiki/w/Chunk_format)

## Building

Use the included Gradle Wrapper with JDK 21 or newer. Gradle provisions the JDK 25
compiler and JDK 21 paperweight toolchain when needed; plugin bytecode targets Java 21.

```sh
bash ./gradlew build
```

On Windows, run `.\gradlew.bat build`.
The server-ready, Mojang-mapped plugin is `build/libs/Regionerator-XXX-SNAPSHOT-FORK.jar`.
The `-plain.jar` is the unshaded development artifact.

Useful tasks:

- `bash ./gradlew test` runs the tests.
- `bash ./gradlew spotlessCheck` checks formatting; `bash ./gradlew spotlessApply` fixes it.
- `bash ./gradlew publishToMavenLocal` publishes the shaded artifact for local consumers.

The build uses the Folia 1.21.11 development bundle through paperweight-userdev.

## Notes

Please refer to [the wiki](https://github.com/Jikoo/Regionerator/wiki) for more information.

This work is licensed under a [Creative Commons Attribution-ShareAlike 4.0 International License](http://creativecommons.org/licenses/by-sa/4.0/).  

## Licenses
The [LinearRegionFile class](/src/main/java/com/github/jikoo/regionerator/world/impl/linear/LinearRegionFile.java) is from [LuminolMC/Luminol](https://github.com/LuminolMC/Luminol)(The repo is deleted, you can also see [Leaf](https://github.com/Winds-Studio/Leaf)) under [GPL v3 License](LinearRegionFile_LICENSE.md).

The [BufferedLinearRegionFile class](/src/main/java/com/github/jikoo/regionerator/world/impl/blinear/BufferedLinearRegionFile.java), BufferedRegionFile, BufferedLinearRegionFileFlusher is from [LophineLabs/Lophine](https://github.com/LophineLabs/Lophine) under [GPL v3 License](BufferedLinearRegionFile_LICENSE.md).

## Disclaimer
Regionerator directly modifies and deletes region files.    
**This cannot be undone — keep regular backups!**  
You are solely responsible for any damages to your world or server incurred by using this plugin. Use at your own risk.
