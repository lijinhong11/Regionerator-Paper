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
package com.github.jikoo.regionerator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class DeletionRunnableTest {

    @Test
    void comparesExpiredChunksByCoordinatesWithoutLoadingChunks() {
        World world = proxy(World.class, null);
        Chunk chunk = proxy(Chunk.class, (method, arguments) -> switch (method) {
            case "getWorld" -> world;
            case "getX" -> 3;
            case "getZ" -> -4;
            default -> null;
        });

        assertTrue(DeletionRunnable.containsChunk(List.of(chunk), world, 3, -4));
        assertFalse(DeletionRunnable.containsChunk(List.of(chunk), world, 3, -3));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, arguments) -> handler == null ? null : handler.invoke(method.getName(), arguments));
    }

    private interface Handler {
        Object invoke(String method, Object[] arguments);
    }
}
