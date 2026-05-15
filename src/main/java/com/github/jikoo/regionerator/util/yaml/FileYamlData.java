/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.util.yaml;

import com.github.jikoo.regionerator.Regionerator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public abstract class FileYamlData extends YamlData {

	public FileYamlData(@NotNull Regionerator plugin, @NotNull File file) {
		super(plugin, () -> YamlConfiguration.loadConfiguration(file), config -> {
			try {
				config.save(file);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

}
