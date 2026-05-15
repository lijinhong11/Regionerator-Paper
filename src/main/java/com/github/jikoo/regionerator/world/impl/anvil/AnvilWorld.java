/*
 * Copyright (c) 2015-2023 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.world.impl.anvil;

import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
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
import java.util.stream.Stream;

public class AnvilWorld extends WorldInfo {

	public AnvilWorld(@NotNull Regionerator plugin, @NotNull World world) {
		super(plugin, world);
	}

	@Override
	public @NotNull RegionInfo getRegion(int regionX, int regionZ) {
		Path dataFolder = findWorldDataFolder().toPath();
		return new AnvilRegion(this, dataFolder, regionX, regionZ, "r.%s.%s.mca");
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
				.map(fileName -> parseRegion(dataFolder, fileName))
				.filter(Objects::nonNull);
	}

	private @Nullable RegionInfo parseRegion(Path dataFolder, String fileName) {
		Matcher matcher = RegionFile.FILE_NAME_PATTERN.matcher(fileName);
		if (!matcher.matches() || !getPlugin().isEnabled()) {
			return null;
		}
		return new AnvilRegion(this, dataFolder, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), "r.%s.%s" + matcher.group(3));
	}

}
