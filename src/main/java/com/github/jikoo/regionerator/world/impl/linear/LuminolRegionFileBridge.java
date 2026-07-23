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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.List;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

final class LuminolRegionFileBridge {
    private final List<RegionFileAccess> accesses;
    private final IOAction beforeClear;

    LuminolRegionFileBridge(List<RegionFileAccess> accesses) {
        this(accesses, () -> {});
    }

    private LuminolRegionFileBridge(List<RegionFileAccess> accesses, IOAction beforeClear) {
        this.accesses = List.copyOf(accesses);
        this.beforeClear = beforeClear;
    }

    static @Nullable LuminolRegionFileBridge create(World world) {
        try {
            ClassLoader loader = world.getClass().getClassLoader();
            Class<?> regionFile = Class.forName("abomination.IRegionFile", false, loader);
            Class<?> chunkPos = Class.forName("net.minecraft.world.level.ChunkPos", false, loader);
            Object level = world.getClass().getMethod("getHandle").invoke(world);
            List<RegionFileAccess> accesses = List.of(
                    reflectiveAccess(level, "moonrise$getChunkDataController", regionFile, chunkPos),
                    reflectiveAccess(level, "moonrise$getEntityChunkDataController", regionFile, chunkPos),
                    reflectiveAccess(level, "moonrise$getPoiChunkDataController", regionFile, chunkPos));
            Class<?> io = Class.forName(
                    "ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO", false, loader);
            Method flush = findMethod(io, "flush", level.getClass());
            return new LuminolRegionFileBridge(accesses, () -> invoke(flush, null, level));
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException e) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?> argument) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argument)) return method;
        }
        throw new NoSuchMethodException(type.getName() + '#' + name);
    }

    private static RegionFileAccess reflectiveAccess(
            Object level, String controllerMethod, Class<?> regionFile, Class<?> chunkPos)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object controller = level.getClass().getMethod(controllerMethod).invoke(level);
        Object cache = controller.getClass().getMethod("getCache").invoke(controller);
        Method getFile = cache.getClass().getMethod("moonrise$getRegionFileIfExists", int.class, int.class);
        Method clear = regionFile.getMethod("clear", chunkPos);
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
                            chunkPos.getConstructor(int.class, int.class).newInstance(chunkX, chunkZ));
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
