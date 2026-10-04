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
package com.github.jikoo.regionerator.world.impl.blinear;

import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BufferedLinearWorld extends WorldInfo {
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)(\\.linear)$");

    private final BufferedLinearRegionFileFlusher flusher;

    public BufferedLinearWorld(@NotNull Regionerator plugin, @NotNull World world) {
        super(plugin, world);
        this.flusher = new BufferedLinearRegionFileFlusher(
                Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4), 1_000, 1_000);
    }

    @Override
    public @NotNull RegionInfo getRegion(int regionX, int regionZ) {
        return createRegionFile("region/r." + regionX + "." + regionZ + ".linear", regionX, regionZ);
    }

    @Override
    public @NotNull Stream<RegionInfo> getRegions() {
        Path dataFolder = findWorldDataFolder().toPath();
        List<String> fileNames = new ArrayList<>();
        File folder = dataFolder.resolve("region").toFile();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                fileNames.add("region/" + file.getName());
            }
        }

        // Some servers may use settings that cause runs to never complete prior to server restarts.
        // Randomize order to improve eventual-correctness.
        Collections.shuffle(fileNames, ThreadLocalRandom.current());

        return distinctRegionPaths(fileNames).stream().map(this::parseRegion).filter(Objects::nonNull);
    }

    private @Nullable RegionInfo parseRegion(String relativePath) {
        Matcher matcher =
                FILE_NAME_PATTERN.matcher(Path.of(relativePath).getFileName().toString());
        if (!matcher.matches() || !getPlugin().isEnabled()) {
            return null;
        }

        try {
            return createRegionFile(
                    relativePath, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        } catch (IllegalStateException e) {
            getPlugin().getLogger().warning(e.getMessage());
            return null;
        }
    }

    private BufferedLinearRegionFile createRegionFile(String relativePath, int regionX, int regionZ) {
        Path path = findWorldDataFolder().toPath().resolve(relativePath);

        synchronized (this.regions) {
            RegionInfo existing = this.regions.get(relativePath);
            if (existing instanceof BufferedLinearRegionFile file && !file.isClosed()) {
                return file;
            }

            try {
                BufferedLinearRegionFile file = new BufferedLinearRegionFile(
                        this,
                        new ChunkPos(
                                Math.multiplyExact(regionX, RegionInfo.CHUNKS_PER_AXIS),
                                Math.multiplyExact(regionZ, RegionInfo.CHUNKS_PER_AXIS)),
                        path,
                        1,
                        this.flusher);
                this.regions.put(relativePath, file);
                return file;
            } catch (IOException e) {
                throw new IllegalStateException("Unable to open buffered linear region file " + path, e);
            }
        }
    }

    public void close() {
        IOException failure = null;
        for (RegionInfo region : this.regions.values()) {
            if (!(region instanceof BufferedLinearRegionFile file) || file.isClosed()) {
                continue;
            }
            try {
                file.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        this.flusher.shutdown();
        this.regions.clear();
        if (failure != null) {
            throw new IllegalStateException("Unable to close buffered linear regions", failure);
        }
    }
}
