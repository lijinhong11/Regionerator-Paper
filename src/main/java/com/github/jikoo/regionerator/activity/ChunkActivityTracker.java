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
package com.github.jikoo.regionerator.activity;

import com.github.jikoo.regionerator.util.RChunkPos;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Chunk;
import org.bukkit.World;

public class ChunkActivityTracker {
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, ActivityWindow>> activityMap =
            new ConcurrentHashMap<>();

    public void recordActivity(Chunk chunk) {
        long now = System.currentTimeMillis();

        ConcurrentHashMap<Long, ActivityWindow> worldActivity =
                activityMap.computeIfAbsent(chunk.getWorld().getName(), name -> new ConcurrentHashMap<>());
        worldActivity.compute(RChunkPos.asLong(chunk.getX(), chunk.getZ()), (key, window) -> {
            if (window == null) {
                return new ActivityWindow(now);
            }
            window.increment();
            return window;
        });
    }

    public Set<Long> pollExpiredChunks(World world, int days, int minInteractions) {
        ConcurrentHashMap<Long, ActivityWindow> worldActivity = activityMap.get(world.getName());
        if (worldActivity == null) {
            return Set.of();
        }

        long now = System.currentTimeMillis();
        long windowMillis = TimeUnit.DAYS.toMillis(days);

        Set<Long> expired = new HashSet<>();

        Iterator<Map.Entry<Long, ActivityWindow>> it = worldActivity.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ActivityWindow> entry = it.next();

            ActivityWindow window = entry.getValue();

            if (now - window.getWindowStart() < windowMillis) continue;

            if (minInteractions > 0 && window.getCount() < minInteractions) {
                expired.add(entry.getKey());
            }

            it.remove();
        }

        return expired;
    }

    public static final class ActivityWindow {
        private final long windowStart;
        private int count;

        public ActivityWindow(long windowStart) {
            this.windowStart = windowStart;
            this.count = 1;
        }

        public synchronized void increment() {
            count++;
        }

        public long getWindowStart() {
            return windowStart;
        }

        public synchronized int getCount() {
            return count;
        }
    }
}
