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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.luben.zstd.ZstdOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.openhft.hashing.LongHashFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinearRegionTest {

    private static final long SUPERBLOCK = 0xc3ff13183cca9d9aL;

    @TempDir
    Path tempDir;

    @Test
    void convertsRegionCoordinatesToLowestChunkCoordinates() {
        LinearRegion region = new LinearRegion(null, tempDir.resolve("r.-2.3.linear"), 1, -2, 3);

        assertEquals(-64, region.getLowestChunkX());
        assertEquals(96, region.getLowestChunkZ());
    }

    @Test
    void readsChunkExistenceAndTimestampFromLazyBucket() throws IOException {
        Path path = tempDir.resolve("r.0.0.linear");
        writeLinearRegion(path, 1234L, new byte[] {1, 2, 3});
        LinearRegion region = new LinearRegion(null, path, 1, 0, 0);

        assertTrue(region.read());
        assertFalse(region.isChunkOrphaned(0, 0));
        assertEquals(1_234_000L, region.getChunkLastModified(0, 0));
        assertTrue(region.isChunkOrphaned(1, 0));
    }

    @Test
    void writesOrphanedChunkSynchronously() throws IOException {
        Path path = tempDir.resolve("r.0.0.linear");
        writeLinearRegion(path, 1234L, new byte[] {1, 2, 3});
        LinearRegion region = new LinearRegion(null, path, 1, 0, 0);

        assertTrue(region.read());
        region.orphanChunk(0, 0);
        assertTrue(region.write());

        LinearRegion reloaded = new LinearRegion(null, path, 1, 0, 0);
        assertTrue(reloaded.read());
        assertTrue(reloaded.isChunkOrphaned(0, 0));
        assertFalse(reloaded.isChunkOrphaned(1, 1));
    }

    @Test
    void writesCoordinatesFromConstructorInsteadOfParsingFileName() throws IOException {
        Path path = tempDir.resolve("not-a-region-name.linear");
        writeLinearRegion(path, 1234L, new byte[] {1, 2, 3});
        LinearRegion region = new LinearRegion(null, path, 1, -2, 3);

        assertTrue(region.read());
        region.orphanChunk(0, 0);
        assertTrue(region.write());

        ByteBuffer file = ByteBuffer.wrap(Files.readAllBytes(path));
        assertEquals(-2, file.getInt(18));
        assertEquals(3, file.getInt(22));
    }

    private static void writeLinearRegion(Path path, long timestamp, byte[] chunkData) throws IOException {
        byte[] bucket = createBucket(timestamp, chunkData);
        long bucketHash = LongHashFunction.xx().hashBytes(bucket);

        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeLong(SUPERBLOCK);
            out.writeByte(3);
            out.writeLong(timestamp);
            out.writeByte(8);
            out.writeInt(0);
            out.writeInt(0);

            byte[] existenceBitmap = new byte[128];
            existenceBitmap[0] = (byte) 0x80;
            existenceBitmap[4] = 0x40;
            out.write(existenceBitmap);
            out.writeByte(0);

            for (int bucketIndex = 0; bucketIndex < 64; ++bucketIndex) {
                if (bucketIndex == 0) {
                    out.writeInt(bucket.length);
                    out.writeByte(1);
                    out.writeLong(bucketHash);
                } else {
                    out.writeInt(0);
                    out.writeByte(1);
                    out.writeLong(0);
                }
            }

            out.write(bucket);
            out.writeLong(SUPERBLOCK);
        }
    }

    private static byte[] createBucket(long timestamp, byte[] chunkData) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZstdOutputStream zstd = new ZstdOutputStream(bytes, 1);
                DataOutputStream out = new DataOutputStream(zstd)) {
            for (int chunkX = 0; chunkX < 4; ++chunkX) {
                for (int chunkZ = 0; chunkZ < 4; ++chunkZ) {
                    boolean present = (chunkX == 0 && chunkZ == 0) || (chunkX == 1 && chunkZ == 1);
                    if (present) {
                        out.writeInt(chunkData.length + Long.BYTES);
                        out.writeLong(timestamp);
                        out.write(chunkData);
                    } else {
                        out.writeInt(0);
                        out.writeLong(0);
                    }
                }
            }
        }
        return bytes.toByteArray();
    }
}
