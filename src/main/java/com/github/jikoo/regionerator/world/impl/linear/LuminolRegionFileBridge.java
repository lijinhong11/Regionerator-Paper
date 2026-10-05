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
package com.github.jikoo.regionerator.world.impl.linear;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.Nullable;

final class LuminolRegionFileBridge {
    private final List<RegionFileAccess> accesses;
    private final IOAction beforeClear;

    private LuminolRegionFileBridge(List<RegionFileAccess> accesses, IOAction beforeClear) {
        this.accesses = List.copyOf(accesses);
        this.beforeClear = beforeClear;
    }

    static @Nullable LuminolRegionFileBridge create(World world) {
        try {
            ClassLoader loader = world.getClass().getClassLoader();
            Class<?> regionFile = Class.forName("abomination.IRegionFile", false, loader);
            ServerLevel level = ((CraftWorld) world).getHandle();
            List<RegionFileAccess> accesses = List.of(
                    reflectiveAccess(level, "moonrise$getChunkDataController", regionFile),
                    reflectiveAccess(level, "moonrise$getEntityChunkDataController", regionFile),
                    reflectiveAccess(level, "moonrise$getPoiChunkDataController", regionFile));
            return new LuminolRegionFileBridge(accesses, () -> MoonriseRegionFileIO.flush(level));
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException e) {
            return null;
        }
    }

    private static RegionFileAccess reflectiveAccess(Object level, String controllerMethod, Class<?> regionFile)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object controller = level.getClass().getMethod(controllerMethod).invoke(level);
        Object cache = controller.getClass().getMethod("getCache").invoke(controller);
        Method getFile = cache.getClass().getMethod("moonrise$getRegionFileIfExists", int.class, int.class);
        Method clear = regionFile.getMethod("clear", ChunkPos.class);
        Method flush = regionFile.getMethod("flush");
        Method getPath = regionFile.getMethod("getPath");
        return new RegionFileAccess() {
            private Object file;

            @Override
            public boolean exists(int chunkX, int chunkZ) throws IOException {
                file = invoke(getFile, cache, chunkX, chunkZ);
                return file != null;
            }

            @Override
            public void clear(int chunkX, int chunkZ) throws IOException {
                try {
                    invoke(
                            clear,
                            file,
                            ((Class<?>) ChunkPos.class)
                                    .getConstructor(int.class, int.class)
                                    .newInstance(chunkX, chunkZ));
                } catch (ReflectiveOperationException e) {
                    throw new IOException(e);
                }
            }

            @Override
            public void flush() throws IOException {
                invoke(flush, file);
            }

            @Override
            public void delete() throws IOException {
                Files.deleteIfExists(invoke(getPath, file));
            }
        };
    }

    private static <T> T invoke(Method method, @Nullable Object receiver, Object... arguments) throws IOException {
        try {
            @SuppressWarnings("unchecked")
            T result = (T) method.invoke(receiver, arguments);
            return result;
        } catch (IllegalAccessException e) {
            throw new IOException(e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException ioException) throw ioException;
            throw new IOException(e.getCause());
        }
    }

    boolean clear(int regionX, int regionZ, BitSet chunks) throws IOException {
        synchronize();
        int lowestChunkX = Math.multiplyExact(regionX, 32);
        int lowestChunkZ = Math.multiplyExact(regionZ, 32);
        boolean handled = false;
        for (RegionFileAccess access : accesses) {
            if (!access.exists(lowestChunkX, lowestChunkZ)) continue;
            for (int index = chunks.nextSetBit(0); index >= 0; index = chunks.nextSetBit(index + 1)) {
                access.clear(lowestChunkX + (index & 31), lowestChunkZ + (index >>> 5));
            }
            access.flush();
            if (chunks.cardinality() == 1024) access.delete();
            handled = true;
        }
        return handled;
    }

    void synchronize() throws IOException {
        beforeClear.run();
    }

    interface RegionFileAccess {
        boolean exists(int chunkX, int chunkZ) throws IOException;

        void clear(int chunkX, int chunkZ) throws IOException;

        void flush() throws IOException;

        void delete() throws IOException;
    }

    private interface IOAction {
        void run() throws IOException;
    }
}
