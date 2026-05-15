package com.github.jikoo.regionerator.activity;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ChunkActivityTracker {
    private final ConcurrentHashMap<Chunk, ActivityWindow> activityMap = new ConcurrentHashMap<>();

    public void recordActivity(Chunk chunk) {
        long now = System.currentTimeMillis();

        activityMap.compute(chunk, (c, window) -> {
            if (window == null) {
                return new ActivityWindow(now);
            }
            window.increment();
            return window;
        });
    }

    public List<Chunk> pollExpiredChunks(World world, int days) {
        long now = System.currentTimeMillis();
        long windowMillis = TimeUnit.DAYS.toMillis(days);

        List<Chunk> expired = new ArrayList<>();

        Iterator<Map.Entry<Chunk, ActivityWindow>> it = activityMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Chunk, ActivityWindow> entry = it.next();

            Chunk chunk = entry.getKey();
            if (!chunk.getWorld().equals(world)) continue;

            ActivityWindow window = entry.getValue();

            if (now - window.getWindowStart() < windowMillis) continue;

            expired.add(chunk);

            it.remove();
        }

        return expired;
    }

    public static final class ActivityWindow {
        private final long windowStart; // 窗口起始时间
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
