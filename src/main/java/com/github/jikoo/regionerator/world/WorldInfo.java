/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.world;

import com.github.jikoo.regionerator.Regionerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A container used to generate {@link RegionInfo} for a {@link World}.
 */
public abstract class WorldInfo {

	private final @NotNull Regionerator plugin;
	private final @NotNull World world;
	protected final @NotNull Map<String, RegionInfo> regions;

	public WorldInfo(@NotNull Regionerator plugin, @NotNull World world) {
		this.plugin = plugin;
		this.world = world;
		regions = new ConcurrentHashMap<>();
	}

	/**
	 * Gets the {@link World} the WorldInfo represents.
	 *
	 * @return the Bukkit world
	 */
	public @NotNull World getWorld() {
		return world;
	}

	/**
	 * Gets {@link RegionInfo} for the specified region coordinates. Note that the region may not exist - check {@link RegionInfo#exists()}.
	 *
	 * @param regionX the region's X coordinate
	 * @param regionZ the region's Z coordinate
	 * @return the {@link RegionInfo}
	 */
	public abstract @NotNull RegionInfo getRegion(int regionX, int regionZ);

	/**
	 * Gets a {@link Stream<RegionInfo>} requesting every {@link RegionInfo} contained by the WorldInfo.
	 *
	 * @return a {@link Stream<RegionInfo>}
	 */
	public abstract @NotNull Stream<RegionInfo> getRegions();

	/**
	 * Gets the instance of Regionerator loading the WorldInfo.
	 *
	 * @return the Regionerator instance
	 */
	protected @NotNull Regionerator getPlugin() {
		return plugin;
	}

	protected @NotNull File findWorldDataFolder() {
		World world = getWorld();
		World defaultWorld = Bukkit.getWorlds().get(0);

		if (world.equals(defaultWorld)) {
			// World is the default world.
			return getDimFolder(world.getEnvironment(), world.getWorldFolder());
		}

		String defaultWorldFolder = defaultWorld.getWorldFolder().getAbsolutePath();
		File worldFolder = world.getWorldFolder();
		if (defaultWorldFolder.equals(worldFolder.getAbsolutePath())) {
			// This is not a Craftbukkit-based Bukkit implementation.
			// The world is not the default world but the world folder is the default world's folder.
			// Determining which folder is actually the folder for this world's data would require us to parse
			// level.dat and determine which world matches, but this is really more of a platform bug.
			throw new IllegalStateException("Cannot determine world data directory! Platform has provided base world directory instead of dimension directory.");
		}

		return getDimFolder(world.getEnvironment(), worldFolder);
	}

	private static @NotNull File getDimFolder(@NotNull World.Environment environment, @NotNull File worldFolder) {
		File bukkitDimFolder = switch (environment) {
			case NETHER -> new File(worldFolder, "DIM-1");
			case THE_END -> new File(worldFolder, "DIM1");
			default -> worldFolder;
		};

		if (bukkitDimFolder.isDirectory()) {
			// Default Bukkit behavior.
			return bukkitDimFolder;
		}

		// Unknown but presumably good platform. We should already be inside the correct dimension folder.
		return worldFolder;
	}
}
