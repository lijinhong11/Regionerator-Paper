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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public record RChunkPos(int x, int z) {
    private static Pair<VarHandle, VarHandle> MC_X_Z_FIELD;
    private static Pair<MethodHandle, MethodHandle> MC_X_Z_METHOD;

    static {
        try {
            var lookup =
                    MethodHandles.privateLookupIn(net.minecraft.world.level.ChunkPos.class, MethodHandles.lookup());
            var x1 = lookup.findVarHandle(int.class, "x", net.minecraft.world.level.ChunkPos.class);
            var z1 = lookup.findVarHandle(int.class, "z", net.minecraft.world.level.ChunkPos.class);
            MC_X_Z_FIELD = new ImmutablePair<>(x1, z1);
        } catch (Exception e) {
            try {
                var lookup = MethodHandles.lookup();
                var x2 = lookup.findVirtual(
                        net.minecraft.world.level.ChunkPos.class, "x", MethodType.methodType(int.class));
                var z2 = lookup.findVirtual(
                        net.minecraft.world.level.ChunkPos.class, "z", MethodType.methodType(int.class));
                MC_X_Z_METHOD = new ImmutablePair<>(x2, z2);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static int getMinecraftChunkPosX(net.minecraft.world.level.ChunkPos pos) {
        if (MC_X_Z_FIELD != null) {
            return (int) MC_X_Z_FIELD.getLeft().getAcquire(pos);
        } else {
            try {
                return (int) MC_X_Z_METHOD.getLeft().invoke(pos);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static int getMinecraftChunkPosZ(net.minecraft.world.level.ChunkPos pos) {
        if (MC_X_Z_FIELD != null) {
            return (int) MC_X_Z_FIELD.getRight().getAcquire(pos);
        } else {
            try {
                return (int) MC_X_Z_METHOD.getRight().invoke(pos);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static long asLong(int x, int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    public net.minecraft.world.level.ChunkPos toMinecraftChunkPos() {
        return new net.minecraft.world.level.ChunkPos(x, z);
    }
}
