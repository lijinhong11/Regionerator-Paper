# Regionerator
A Bukkit plugin for gradually deleting unused area, allowing you to free up disk space and get rid of old builds or devastated land.

### This is a fork version for 1.20.6+ and Paper/Folia servers!  
### Changes on many classes! So it never supports Spigot!!!
  
## Features compared to the original plugin
1. Linear & BLinear format support
   * See what's Linear: <https://github.com/xymb-endcrystalme/LinearRegionFileFormatTools>
   * BLinear is the recoded version of Linear, see what's BLinear: <https://github.com/lijinhong11/Regionerator-Paper/blob/master/Linear-vs-BLinear.md>
2. Folia support
3. Can configure interaction times and inhabitedtime checks to the region  
   (If you don't know what inhabited time is, see: <https://minecraft.wiki/w/Chunk_format>)
4. Automatically preserves chunks containing trial chamber structure starts or references, even when activity-based deletion criteria are met.

## Download
- 简体中文链接：<https://www.minebbs.com/resources/regionerator-paper.17578/>
- [GitHub Actions](https://github.com/lijinhong11/Regionerator-Paper/actions)

## Building

Requires JDK 21 or newer

Run:
```bash
./gradlew build
```

## Notes

Please refer to [the wiki](https://github.com/Jikoo/Regionerator/wiki) for more information.

## Disclaimer
Regionerator directly modifies and deletes region files.    
**This cannot be undone — keep regular backups!**  
You are solely responsible for any damages to your world or server incurred by using this plugin. Use at your own risk.

## Licenses
The Regionerator plugin is licensed under [Creative Commons Attribution-ShareAlike 4.0 International License](http://creativecommons.org/licenses/by-sa/4.0/).

The [LinearRegionFile class](/src/main/java/com/github/jikoo/regionerator/world/impl/linear/LinearRegionFile.java) is from [LuminolMC/Luminol](https://github.com/LuminolMC/Luminol)(The repo is deleted, you can also see [Leaf](https://github.com/Winds-Studio/Leaf)) under [GPL v3 License](LinearRegionFile_LICENSE.md).

The [BufferedLinearRegionFile class](/src/main/java/com/github/jikoo/regionerator/world/impl/blinear/BufferedLinearRegionFile.java), BufferedRegionFile, BufferedLinearRegionFileFlusher is from [LophineLabs/Lophine](https://github.com/LophineLabs/Lophine) under [GPL v3 License](BufferedLinearRegionFile_LICENSE.md).