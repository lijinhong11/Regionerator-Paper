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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class LuminolRegionFileBridgeTest {

    @Test
    void clearsEveryDataStorageAndFlushesLoadedFiles() throws IOException {
        List<FakeAccess> accesses = List.of(new FakeAccess(true), new FakeAccess(true), new FakeAccess(false));
        LuminolRegionFileBridge bridge = new LuminolRegionFileBridge(new ArrayList<>(accesses));
        BitSet chunks = new BitSet(1024);
        chunks.set(0);
        chunks.set(33);

        assertTrue(bridge.clear(2, -1, chunks));

        assertEquals(List.of("64,-32", "65,-31"), accesses.get(0).cleared);
        assertEquals(List.of("64,-32", "65,-31"), accesses.get(1).cleared);
        assertTrue(accesses.get(2).cleared.isEmpty());
        assertEquals(1, accesses.get(0).flushes);
        assertEquals(1, accesses.get(1).flushes);
        assertEquals(0, accesses.get(2).flushes);
    }

    @Test
    void deletesEmptyFilesAfterClearingWholeRegion() throws IOException {
        FakeAccess access = new FakeAccess(true);
        LuminolRegionFileBridge bridge = new LuminolRegionFileBridge(List.of(access));
        BitSet chunks = new BitSet(1024);
        chunks.set(0, 1024);

        assertTrue(bridge.clear(0, 0, chunks));

        assertEquals(1, access.deletes);
    }

    private static class FakeAccess implements LuminolRegionFileBridge.RegionFileAccess {
        private final boolean exists;
        private final List<String> cleared = new ArrayList<>();
        private int flushes;
        private int deletes;

        private FakeAccess(boolean exists) {
            this.exists = exists;
        }

        @Override
        public boolean exists(int chunkX, int chunkZ) {
            return exists;
        }

        @Override
        public void clear(int chunkX, int chunkZ) {
            cleared.add(chunkX + "," + chunkZ);
        }

        @Override
        public void flush() {
            ++flushes;
        }

        @Override
        public void delete() {
            ++deletes;
        }
    }
}
