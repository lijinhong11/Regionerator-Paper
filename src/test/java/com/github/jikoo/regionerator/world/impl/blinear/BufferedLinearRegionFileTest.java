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
package com.github.jikoo.regionerator.world.impl.blinear;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class BufferedLinearRegionFileTest {
    @TempDir
    Path directory;

    private final BufferedLinearRegionFileFlusher flusher =
            new BufferedLinearRegionFileFlusher(1, Long.MAX_VALUE, Long.MAX_VALUE);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void shutdown() {
        flusher.shutdown();
    }

    @Test
    void preservesDataAndTimestampsAcrossRewriteAppendAndReopen() throws Exception {
        Path path = directory.resolve("r.0.0.b_linear");
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos second = new ChunkPos(0, 2); // a different bucket
        byte[] firstData = {1, 2, 3, 4};
        byte[] secondData = {5, 6, 7};
        long firstTimestamp;
        long secondTimestamp;

        try (BufferedLinearRegionFile file = open(path)) {
            file.write(first, ByteBuffer.wrap(firstData));
            file.write(second, ByteBuffer.wrap(secondData));
            firstTimestamp = timestamp(file, first);
            secondTimestamp = timestamp(file, second);
            assertTrue(firstTimestamp > 0);
            assertTrue(secondTimestamp > 0);
        }

        try (BufferedLinearRegionFile file = open(path)) {
            assertChunk(file, first, firstData, firstTimestamp);
            assertChunk(file, second, secondData, secondTimestamp);
            firstData = new byte[] {8, 9};
            file.write(first, ByteBuffer.wrap(firstData));
            firstTimestamp = timestamp(file, first);
            file.syncIfNeeded();
        }

        try (BufferedLinearRegionFile file = open(path)) {
            assertChunk(file, first, firstData, firstTimestamp);
            assertChunk(file, second, secondData, secondTimestamp);
            file.clear(first);
            assertEquals(0, timestamp(file, first));
            file.syncIfNeeded();
        }

        try (BufferedLinearRegionFile file = open(path)) {
            assertFalse(file.doesChunkExist(first));
            assertEquals(0, timestamp(file, first));
            assertChunk(file, second, secondData, secondTimestamp);
            file.clear(second);
        }

        try (BufferedLinearRegionFile file = open(path)) {
            assertFalse(file.doesChunkExist(first));
            assertFalse(file.doesChunkExist(second));
        }
    }

    @Test
    void retainsCachedTimestampWhenSwapIsCompacted() throws Exception {
        Path path = directory.resolve("r.0.0.b_linear");
        ChunkPos survivor = new ChunkPos(1, 0);
        byte[] survivorData = {11, 12, 13};
        long timestamp;
        try (BufferedLinearRegionFile file = open(path)) {
            byte[] garbage = new byte[2 * 1024 * 1024];
            new Random(42).nextBytes(garbage);
            file.write(new ChunkPos(0, 0), ByteBuffer.wrap(garbage));
            file.write(survivor, ByteBuffer.wrap(survivorData));
            timestamp = timestamp(file, survivor);
            file.clear(new ChunkPos(0, 0));
            file.flush(); // More than 1 MiB and 60% garbage triggers swap compaction.
            assertChunk(file, survivor, survivorData, timestamp);
        }
        try (BufferedLinearRegionFile file = open(path)) {
            assertChunk(file, survivor, survivorData, timestamp);
        }
    }

    @Test
    void directWriteCreatesMasterFileBeforeClose() throws Exception {
        Path path = directory.resolve("r.0.0.b_linear");
        ChunkPos pos = new ChunkPos(0, 0);
        byte[] data = {1, 2, 3};
        long timestamp;
        try (BufferedLinearRegionFile file = open(path)) {
            file.write(pos, ByteBuffer.wrap(data));
            timestamp = timestamp(file, pos);
            assertTrue(Files.isRegularFile(path), "A fresh region must be persisted on its first direct write");
            assertFalse(file.shouldSync());
        }
        try (BufferedLinearRegionFile file = open(path)) {
            assertChunk(file, pos, data, timestamp);
        }
    }

    @Test
    void moonriseWriteCommitsOnlyWhenCallbackRuns() throws Exception {
        Path path = directory.resolve("r.0.0.b_linear");
        ChunkPos pos = new ChunkPos(0, 0);
        byte[] data = {4, 5, 6};
        long timestamp;
        try (BufferedLinearRegionFile file = open(path)) {
            var write = file.moonrise$startWrite(new CompoundTag(), pos);
            write.output().write(data);
            write.output().close();
            assertFalse(file.doesChunkExist(pos), "Closing the serialization stream must not commit the chunk");
            assertFalse(Files.exists(path));

            write.write().run(null);
            timestamp = timestamp(file, pos);
            assertChunk(file, pos, data, timestamp);
            assertTrue(Files.isRegularFile(path));
        }
        try (BufferedLinearRegionFile file = open(path)) {
            assertChunk(file, pos, data, timestamp);
        }
    }

    @Test
    void forceFlushAllPersistsPendingChangesWithoutClosingFile() throws Exception {
        Path path = directory.resolve("r.0.0.b_linear");
        ChunkPos pos = new ChunkPos(0, 0);
        CountDownLatch synced = new CountDownLatch(1);
        try (BufferedLinearRegionFile file = new BufferedLinearRegionFile(null, new ChunkPos(0, 0), path, 1, flusher) {
            @Override
            public void syncIfNeeded() throws IOException {
                super.syncIfNeeded();
                synced.countDown();
            }
        }) {
            file.write(pos, ByteBuffer.wrap(new byte[] {1, 2}));
            long previousSize = Files.size(path);
            file.write(pos, ByteBuffer.wrap(new byte[] {3, 4, 5}));
            assertTrue(file.shouldSync());

            flusher.forceFlushAll();
            assertTrue(synced.await(5, TimeUnit.SECONDS), "Forced synchronization must ignore the idle timeout");
            assertFalse(file.isClosed());
            assertFalse(file.shouldSync());
            assertTrue(Files.size(path) > previousSize, "The changed bucket must be appended to the master file");
        }
    }

    private BufferedLinearRegionFile open(Path path) throws Exception {
        return new BufferedLinearRegionFile(null, new ChunkPos(0, 0), path, 1, flusher);
    }

    private static void assertChunk(BufferedLinearRegionFile file, ChunkPos pos, byte[] data, long timestamp)
            throws Exception {
        assertTrue(file.doesChunkExist(pos));
        assertEquals(timestamp, timestamp(file, pos));
        try (var input = file.getChunkDataInputStream(pos)) {
            assertNotNull(input);
            assertArrayEquals(data, input.readAllBytes());
        }
    }

    private static long timestamp(BufferedLinearRegionFile file, ChunkPos pos) throws Exception {
        // Exercise the storage query without constructing ChunkInfo's Bukkit visit cache.
        var method = BufferedLinearRegionFile.class.getDeclaredMethod("getChunkTimestamp", int.class);
        method.setAccessible(true);
        return (long) method.invoke(file, (pos.x & 31) + ((pos.z & 31) << 5));
    }
}
