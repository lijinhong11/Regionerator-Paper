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
package com.github.jikoo.regionerator.util.yaml;

import com.github.jikoo.regionerator.Regionerator;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

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
