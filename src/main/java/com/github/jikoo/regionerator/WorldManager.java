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
package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.world.WorldInfo;
import com.github.jikoo.regionerator.world.impl.anvil.AnvilWorld;
import com.github.jikoo.regionerator.world.impl.blinear.BufferedLinearWorld;
import com.github.jikoo.regionerator.world.impl.linear.LinearWorld;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class WorldManager {

    private final @NotNull Regionerator plugin;
    private final @NotNull Map<String, WorldInfo> worlds;

    private final @NotNull RegionImplementation regionImplementation;

    public WorldManager(@NotNull Regionerator plugin, @NotNull RegionImplementation regionImplementation) {
        this.plugin = plugin;
        this.worlds = new HashMap<>();
        this.regionImplementation = regionImplementation;
    }

    public @NotNull WorldInfo getWorld(@NotNull World world) {
        return worlds.computeIfAbsent(world.getName(), (name) -> getWorldImpl(world));
    }

    public void releaseWorld(@NotNull World world) {
        WorldInfo worldInfo = worlds.remove(world.getName());
        if (worldInfo instanceof BufferedLinearWorld bufferedLinearWorld) {
            bufferedLinearWorld.close();
        } else if (worldInfo instanceof LinearWorld linearWorld) {
            linearWorld.close();
        }
    }

    public void close() {
        worlds.values().forEach(worldInfo -> {
            if (worldInfo instanceof BufferedLinearWorld bufferedLinearWorld) {
                bufferedLinearWorld.close();
            } else if (worldInfo instanceof LinearWorld linearWorld) {
                linearWorld.close();
            }
        });
        worlds.clear();
    }

    private @NotNull WorldInfo getWorldImpl(@NotNull World world) {
        return switch (regionImplementation) {
            case ANVIL -> new AnvilWorld(plugin, world);
            case LINEAR -> new LinearWorld(plugin, world);
            case BLINEAR -> new BufferedLinearWorld(plugin, world);
            case NONE -> throw new IllegalArgumentException();
        };
    }
}
