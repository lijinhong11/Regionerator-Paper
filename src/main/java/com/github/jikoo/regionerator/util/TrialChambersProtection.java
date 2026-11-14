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
package com.github.jikoo.regionerator.util;

import ca.spottedleaf.concurrentutil.executor.Cancellable;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

/*
 * This is used to protect Vaults being inactive.
 * See MC-268618: https://bugs.mojang.com/browse/MC/issues/MC-268618
 */

/** Protects saved trial chamber starts and references without loading or generating chunks. */
public final class TrialChambersProtection {
    private static final String STRUCTURE_ID = "minecraft:trial_chambers";

    private TrialChambersProtection() {}

    /** Called only by the asynchronous deletion worker. Uses the server's configured region format. */
    public static boolean isProtected(World world, int chunkX, int chunkZ)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        return query(
                callback -> MoonriseRegionFileIO.loadDataAsync(
                        ((CraftWorld) world).getHandle(),
                        chunkX,
                        chunkZ,
                        MoonriseRegionFileIO.RegionFileType.CHUNK_DATA,
                        callback,
                        true),
                30,
                TimeUnit.SECONDS);
    }

    static boolean query(Function<BiConsumer<CompoundTag, Throwable>, Cancellable> load, long timeout, TimeUnit unit)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<CompoundTag> result = new CompletableFuture<>();
        Cancellable read = load.apply((data, failure) -> {
            if (failure != null) {
                result.completeExceptionally(failure);
            } else if (data == null) {
                // A chunk present in our deletion snapshot must be readable before deletion is allowed.
                result.completeExceptionally(new IOException("Missing chunk data during trial chamber check"));
            } else {
                result.complete(data);
            }
        });
        try {
            return containsTrialChambers(result.get(timeout, unit));
        } finally {
            result.cancel(false);
            read.cancel();
        }
    }

    static boolean containsTrialChambers(CompoundTag chunk) throws IOException {
        CompoundTag structures = chunk.getCompound("structures").orElse(null);
        if (structures == null) {
            if (chunk.contains("structures")) {
                throw new IOException("Invalid chunk structures data");
            }
            return false;
        }

        CompoundTag starts = structures.getCompound("starts").orElse(null);
        if (starts != null && starts.contains(STRUCTURE_ID)) {
            CompoundTag start =
                    starts.getCompound(STRUCTURE_ID).orElseThrow(() -> new IOException("Invalid trial chamber start"));
            // Vanilla also writes INVALID placeholders for chunks where no structure started.
            String id = start.getString("id").orElseThrow(() -> new IOException("Missing trial chamber start ID"));
            if (!"INVALID".equals(id)) {
                return true;
            }
        }

        // References mark the other chunks intersecting the structure's bounding box.
        CompoundTag references = structures.getCompound("References").orElse(null);
        if (references != null && references.contains(STRUCTURE_ID)) {
            return references
                            .getLongArray(STRUCTURE_ID)
                            .orElseThrow(() -> new IOException("Invalid trial chamber references"))
                            .length
                    > 0;
        }
        if (structures.contains("starts") && starts == null
                || structures.contains("References") && references == null) {
            throw new IOException("Invalid chunk structure starts or references");
        }
        return false;
    }
}
