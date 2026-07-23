/*
 * Regionerator
 * Copyright (C) 2026 Jikoo and lijinhong11(mmmjjkx)
 *
 * Regionerator is licensed under a
 * Creative Commons Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */
package com.github.jikoo.regionerator.world.impl.linear;

import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import com.github.jikoo.regionerator.world.impl.anvil.AnvilRegion;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LinearWorld extends WorldInfo {
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)(\\.linear)$");
    private final @Nullable LuminolRegionFileBridge bridge;

    public LinearWorld(@NotNull Regionerator plugin, @NotNull World world) {
        super(plugin, world);
        bridge = LuminolRegionFileBridge.create(world);
    }

    @Override
    public @NotNull RegionInfo getRegion(int regionX, int regionZ) {
        return createRegionFile(regionX, regionZ);
    }

    @Override
    public @NotNull Stream<RegionInfo> getRegions() {
        Path dataFolder = findWorldDataFolder().toPath();
        List<String> fileNames = new ArrayList<>();
        for (String folderName : AnvilRegion.DATA_SUBDIRS) {
            File folder = dataFolder.resolve(folderName).toFile();
            File[] files = folder.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                fileNames.add(folderName + '/' + file.getName());
            }
        }

        // Some servers may use settings that cause runs to never complete prior to server restarts.
        // Randomize order to improve eventual-correctness.
        Collections.shuffle(fileNames, ThreadLocalRandom.current());

        return distinctRegionPaths(fileNames).stream().map(this::parseRegion).filter(Objects::nonNull);
    }

    static List<String> distinctRegionPaths(List<String> paths) {
        LinkedHashMap<String, String> distinct = new LinkedHashMap<>();
        for (String path : paths)
            distinct.putIfAbsent(Path.of(path).getFileName().toString(), path);
        return List.copyOf(distinct.values());
    }

    private @Nullable RegionInfo parseRegion(String relativePath) {
        Matcher matcher =
                FILE_NAME_PATTERN.matcher(Path.of(relativePath).getFileName().toString());
        if (!matcher.matches() || !getPlugin().isEnabled()) {
            return null;
        }

        return createRegionFile(relativePath, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    private LinearRegion createRegionFile(int chunkX, int chunkZ) {
        return createRegionFile("region/r." + chunkX + "." + chunkZ + ".linear", chunkX, chunkZ);
    }

    private LinearRegion createRegionFile(String relativePath, int chunkX, int chunkZ) {
        Path linear = findWorldDataFolder().toPath().resolve(relativePath);
        return new LinearRegion(this, linear, 1, chunkX, chunkZ, bridge);
    }
}
