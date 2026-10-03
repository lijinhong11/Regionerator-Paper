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
package com.github.jikoo.regionerator.commands;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class WorldEditSelectionHandler {

    private WorldEditSelectionHandler() {}

    static @NotNull Set<FlagHandler.ChunkPosition> getSelection(@NotNull Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().getIfPresent(BukkitAdapter.adapt(player));
        if (session == null || session.getSelectionWorld() == null) {
            throw new IllegalStateException("You must select an area with WorldEdit to (un)flag!");
        }

        Region selection;
        try {
            selection = session.getSelection(session.getSelectionWorld());
        } catch (Exception ignored) {
            throw new IllegalStateException("You must select an area with WorldEdit to (un)flag!");
        }

        String worldName = session.getSelectionWorld().getName();
        return selection.getChunks().stream()
                .map(vector -> new FlagHandler.ChunkPosition(worldName, vector.x(), vector.z()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
