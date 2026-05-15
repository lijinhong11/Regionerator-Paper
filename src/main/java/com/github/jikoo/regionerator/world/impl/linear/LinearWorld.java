package com.github.jikoo.regionerator.world.impl.linear;

import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import com.github.jikoo.regionerator.world.impl.anvil.AnvilRegion;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class LinearWorld extends WorldInfo {
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)(\\.linear)$");

    public LinearWorld(@NotNull Regionerator plugin, @NotNull World world) {
        super(plugin, world);
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
                fileNames.add(file.getName());
            }
        }

        // Some servers may use settings that cause runs to never complete prior to server restarts.
        // Randomize order to improve eventual-correctness.
        Collections.shuffle(fileNames, ThreadLocalRandom.current());

        return fileNames.stream()
                .distinct()
                .map(this::parseRegion)
                .filter(Objects::nonNull);
    }

    private @Nullable RegionInfo parseRegion(String fileName) {
        Matcher matcher = FILE_NAME_PATTERN.matcher(fileName);
        if (!matcher.matches() || !getPlugin().isEnabled()) {
            return null;
        }

        return createRegionFile(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    private LinearRegion createRegionFile(int chunkX, int chunkZ) {
        String fileName = "r." + chunkX + "." + chunkZ + ".linear";
        Path linear = getWorld().getWorldPath().resolve("region").resolve(fileName);
        return new LinearRegion(this, linear, 1, chunkX, chunkZ);
    }
}
