/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator;

import com.github.jikoo.regionerator.world.WorldInfo;
import com.github.jikoo.regionerator.world.impl.anvil.AnvilWorld;
import com.github.jikoo.regionerator.world.impl.linear.LinearWorld;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

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
		worlds.remove(world.getName());
	}

	private @NotNull WorldInfo getWorldImpl(@NotNull World world) {
		return switch (regionImplementation) {
			case ANVIL -> new AnvilWorld(plugin, world);
			case LINEAR -> new LinearWorld(plugin, world);
			case NONE -> throw new IllegalArgumentException();
		};
	}
}
