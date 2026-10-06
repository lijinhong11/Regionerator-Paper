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

import static org.junit.jupiter.api.Assertions.*;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinearRegionFileTest {
    private static final long SUPERBLOCK = 0xc3ff13183cca9d9aL;

    @TempDir
    Path directory;

    @Test
    void bufferedFlushPreservesSurvivingChunksAndWritesValidV3File() throws Exception {
        Path path = directory.resolve("r.0.0.linear");
        Files.write(path, legacyRegion());
        LinearRegionFile file = open(path);
        assertTrue(file.read());
        assertFalse(file.isChunkOrphaned(0, 0));
        assertFalse(file.isChunkOrphaned(1, 0));
        long survivorTimestamp = file.getChunkLastModified(1, 0);

        file.orphanChunk(0, 0);
        file.flush();

        assertFalse(Files.exists(directory.resolve("r.0.0.linear.tmp")));
        byte[] saved = Files.readAllBytes(path);
        assertEquals(3, saved[Long.BYTES]);
        assertEquals(SUPERBLOCK, ByteBuffer.wrap(saved).getLong(saved.length - Long.BYTES));

        LinearRegionFile reopened = open(path);
        assertTrue(reopened.read()); // Checks bucket hashes and footer as well as the header.
        assertTrue(reopened.isChunkOrphaned(0, 0));
        assertFalse(reopened.isChunkOrphaned(1, 0));
        assertEquals(survivorTimestamp, reopened.getChunkLastModified(1, 0));
        assertTrue(reopened.isChunkOrphaned(31, 31));
    }

    @Test
    void rejectsLegacyLengthMismatch() throws Exception {
        Path path = directory.resolve("r.0.0.linear");
        byte[] contents = legacyRegion();
        ByteBuffer.wrap(contents).putInt(20, 1); // Declared compressed size does not match the bytes read.
        Files.write(path, contents);
        assertThrows(RuntimeException.class, () -> open(path).read());
    }

    private LinearRegionFile open(Path path) {
        return new LinearRegionFile(null, path, 1, 0, 0, null);
    }

    private static byte[] legacyRegion() throws Exception {
        ByteBuffer chunks = ByteBuffer.allocate(1024 * 8 + 5);
        for (int index = 0; index < 1024; index++) {
            chunks.putInt(index == 0 ? 2 : index == 1 ? 3 : 0);
            chunks.putInt(0);
        }
        chunks.put(new byte[] {1, 2, 3, 4, 5});
        byte[] compressed = Zstd.compress(chunks.array());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeLong(SUPERBLOCK);
            out.writeByte(1);
            out.writeLong(0);
            out.writeByte(1);
            out.writeShort(2);
            out.writeInt(compressed.length);
            out.writeLong(0);
            out.write(compressed);
            out.writeLong(SUPERBLOCK);
        }
        return bytes.toByteArray();
    }
}
