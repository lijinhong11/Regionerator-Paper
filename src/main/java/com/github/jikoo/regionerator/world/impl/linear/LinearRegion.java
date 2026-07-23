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

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import com.github.jikoo.regionerator.world.WorldInfo;
import com.github.jikoo.regionerator.world.impl.anvil.RegionFile;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.BitSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.openhft.hashing.LongHashFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Source is copied from LuminolMC/Luminol<br>
 * Thanks to their effort!
 * <p>
 * Edited by lijinhong11
 */
// LinearRegionFile_implementation_version_0_5byXymb
// Just gonna use this string to inform other forks about updates ;-)
public class LinearRegion extends RegionInfo {
    private static final long SUPERBLOCK = 0xc3ff13183cca9d9aL;
    private static final byte VERSION = 3;
    private static final int HEADER_SIZE = 27;
    private static final int FOOTER_SIZE = 8;

    private byte[][] bucketBuffers;
    private final byte[][] buffer = new byte[1024][];
    private final int[] bufferUncompressedSize = new int[1024];

    private final boolean[] chunkExists = new boolean[1024];
    private final BitSet pendingOrphans = new BitSet(1024);

    private final long[] chunkTimestamps = new long[1024];

    private final LZ4Compressor compressor;
    private final LZ4FastDecompressor decompressor;

    private boolean markedToSave = false;
    public Path regionFile;

    private final int compressionLevel;
    private final int regionX;
    private final int regionZ;
    private final @Nullable LuminolRegionFileBridge bridge;
    private int gridSize = 8;
    private int bucketSize = 4;

    @ApiStatus.Internal
    @TestOnly
    public LinearRegion(WorldInfo worldInfo, Path path, int compressionLevel, int lowestRegionX, int lowestRegionZ) {
        this(worldInfo, path, compressionLevel, lowestRegionX, lowestRegionZ, null);
    }

    LinearRegion(
            WorldInfo worldInfo,
            Path path,
            int compressionLevel,
            int lowestRegionX,
            int lowestRegionZ,
            @Nullable LuminolRegionFileBridge bridge) {
        super(
                worldInfo,
                Math.multiplyExact(lowestRegionX, CHUNKS_PER_AXIS),
                Math.multiplyExact(lowestRegionZ, CHUNKS_PER_AXIS));
        this.regionFile = path;
        this.compressionLevel = compressionLevel;
        this.regionX = lowestRegionX;
        this.regionZ = lowestRegionZ;
        this.bridge = bridge;

        this.compressor = LZ4Factory.fastestInstance().fastCompressor();
        this.decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    }

    private int chunkToBucketIdx(int chunkX, int chunkZ) {
        int bx = chunkX / bucketSize, bz = chunkZ / bucketSize;
        return bx * gridSize + bz;
    }

    private void openBucket(int chunkX, int chunkZ) {
        chunkX = Math.floorMod(chunkX, 32);
        chunkZ = Math.floorMod(chunkZ, 32);
        int idx = chunkToBucketIdx(chunkX, chunkZ);

        if (bucketBuffers == null) return;
        if (bucketBuffers[idx] != null) {
            try (ByteArrayInputStream bucketByteStream = new ByteArrayInputStream(bucketBuffers[idx]);
                    ZstdInputStream zstdStream = new ZstdInputStream(bucketByteStream)) {
                ByteBuffer bucketBuffer = ByteBuffer.wrap(zstdStream.readAllBytes());

                int bx = chunkX / bucketSize, bz = chunkZ / bucketSize;

                for (int cx = 0; cx < 32 / gridSize; cx++) {
                    for (int cz = 0; cz < 32 / gridSize; cz++) {
                        int chunkIndex = (bx * (32 / gridSize) + cx) + (bz * (32 / gridSize) + cz) * 32;

                        int chunkSize = bucketBuffer.getInt();
                        long timestamp = bucketBuffer.getLong();
                        this.chunkTimestamps[chunkIndex] = timestamp;
                        this.chunkExists[chunkIndex] = chunkSize > 0;

                        if (chunkSize > 0) {
                            byte[] chunkData = new byte[chunkSize - 8];
                            bucketBuffer.get(chunkData);

                            int maxCompressedLength = this.compressor.maxCompressedLength(chunkData.length);
                            byte[] compressed = new byte[maxCompressedLength];
                            int compressedLength = this.compressor.compress(
                                    chunkData, 0, chunkData.length, compressed, 0, maxCompressedLength);
                            byte[] finalCompressed = new byte[compressedLength];
                            System.arraycopy(compressed, 0, finalCompressed, 0, compressedLength);

                            this.buffer[chunkIndex] = finalCompressed;
                            this.bufferUncompressedSize[chunkIndex] = chunkData.length;
                        }
                    }
                }
            } catch (IOException | RuntimeException ex) {
                throw new RuntimeException("Region file corrupted: " + regionFile + " bucket: " + idx, ex);
            }
            bucketBuffers[idx] = null;
        }
    }

    public boolean regionFileOpen = false;

    @Override
    @CanIgnoreReturnValue
    public synchronized boolean read() {
        if (regionFileOpen) return true;

        File regionFile = new File(this.regionFile.toString());

        if (!regionFile.canRead()) {
            return false;
        }

        try {
            if (bridge != null) bridge.synchronize();
            byte[] fileContent = Files.readAllBytes(this.regionFile);
            ByteBuffer buffer = ByteBuffer.wrap(fileContent);

            if (fileContent.length < Long.BYTES + Byte.BYTES + Long.BYTES) {
                Files.deleteIfExists(regionFile.toPath());
                return false;
            }

            long superBlock = buffer.getLong();
            if (superBlock != SUPERBLOCK)
                throw new RuntimeException("Invalid superblock: " + superBlock + " file " + this.regionFile);

            byte version = buffer.get();
            if (version == 1 || version == 2) {
                parseLinearV1(buffer);
            } else if (version == 3) {
                parseLinearV2(buffer);
            } else {
                throw new RuntimeException("Invalid version: " + version + " file " + this.regionFile);
            }

            regionFileOpen = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to open region file " + this.regionFile, e);
        }

        return true;
    }

    private void parseLinearV1(ByteBuffer buffer) throws IOException {
        final int HEADER_SIZE = 32;
        final int FOOTER_SIZE = 8;

        // Skip newestTimestamp (Long) + Compression level (Byte) + Chunk count (Short): Unused.
        buffer.position(buffer.position() + 11);

        int dataCount = buffer.getInt();
        long fileLength = this.regionFile.toFile().length();
        if (fileLength != HEADER_SIZE + dataCount + FOOTER_SIZE) {
            throw new IOException("Invalid file length: " + this.regionFile + " " + fileLength + " "
                    + (HEADER_SIZE + dataCount + FOOTER_SIZE));
        }

        buffer.position(buffer.position() + 8); // Skip data hash (Long): Unused.

        byte[] rawCompressed = new byte[dataCount];
        buffer.get(rawCompressed);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(rawCompressed);
        ZstdInputStream zstdInputStream = new ZstdInputStream(byteArrayInputStream);
        ByteBuffer decompressedBuffer = ByteBuffer.wrap(zstdInputStream.readAllBytes());

        int[] starts = new int[1024];
        for (int i = 0; i < 1024; i++) {
            starts[i] = decompressedBuffer.getInt();
            decompressedBuffer.getInt(); // Skip timestamps (Int): Unused.
        }

        for (int i = 0; i < 1024; i++) {
            if (starts[i] > 0) {
                int size = starts[i];
                byte[] chunkData = new byte[size];
                decompressedBuffer.get(chunkData);

                int maxCompressedLength = this.compressor.maxCompressedLength(size);
                byte[] compressed = new byte[maxCompressedLength];
                int compressedLength = this.compressor.compress(chunkData, 0, size, compressed, 0, maxCompressedLength);
                byte[] finalCompressed = new byte[compressedLength];
                System.arraycopy(compressed, 0, finalCompressed, 0, compressedLength);

                this.buffer[i] = finalCompressed;
                this.bufferUncompressedSize[i] = size;
                this.chunkExists[i] = true;
                this.chunkTimestamps[i] = getTimestamp(); // Use current timestamp as we don't have the original
            }
        }
    }

    private void parseLinearV2(ByteBuffer buffer) throws IOException {
        buffer.getLong(); // Skip newestTimestamp (Long)
        gridSize = buffer.get();
        if (gridSize != 1 && gridSize != 2 && gridSize != 4 && gridSize != 8 && gridSize != 16 && gridSize != 32)
            throw new RuntimeException("Invalid grid size: " + gridSize + " file " + this.regionFile);
        bucketSize = 32 / gridSize;

        buffer.getInt(); // Skip region_x (Int)
        buffer.getInt(); // Skip region_z (Int)

        boolean[] chunkExistenceBitmap = deserializeExistenceBitmap(buffer);
        System.arraycopy(chunkExistenceBitmap, 0, chunkExists, 0, chunkExists.length);

        while (true) {
            byte featureNameLength = buffer.get();
            if (featureNameLength == 0) break;
            byte[] featureNameBytes = new byte[featureNameLength];
            buffer.get(featureNameBytes);
            buffer.getInt();
        }

        int[] bucketSizes = new int[gridSize * gridSize];
        byte[] bucketCompressionLevels = new byte[gridSize * gridSize];
        long[] bucketHashes = new long[gridSize * gridSize];
        for (int i = 0; i < gridSize * gridSize; i++) {
            bucketSizes[i] = buffer.getInt();
            bucketCompressionLevels[i] = buffer.get();
            bucketHashes[i] = buffer.getLong();
        }

        bucketBuffers = new byte[gridSize * gridSize][];
        for (int i = 0; i < gridSize * gridSize; i++) {
            if (bucketSizes[i] > 0) {
                bucketBuffers[i] = new byte[bucketSizes[i]];

                buffer.get(bucketBuffers[i]);

                long rawHash = LongHashFunction.xx().hashBytes(bucketBuffers[i]);
                if (rawHash != bucketHashes[i]) throw new IOException("Region file hash incorrect " + this.regionFile);
            }
        }

        long footerSuperBlock = buffer.getLong();
        if (footerSuperBlock != SUPERBLOCK) throw new IOException("Footer superblock invalid " + this.regionFile);
    }

    private synchronized void markToSave() {
        markedToSave = true;
    }

    private synchronized boolean isMarkedToSave() {
        if (!markedToSave) return false;
        markedToSave = false;
        return true;
    }

    public synchronized void flush() throws IOException {
        if (!isMarkedToSave()) return;

        read();

        long timestamp = getTimestamp();

        // writeStart = System.nanoTime();
        File tempFile = new File(regionFile.toString() + ".tmp");
        FileOutputStream fileStream = new FileOutputStream(tempFile);
        DataOutputStream dataStream = new DataOutputStream(fileStream);

        dataStream.writeLong(SUPERBLOCK);
        dataStream.writeByte(VERSION);
        dataStream.writeLong(timestamp);
        dataStream.writeByte(gridSize);

        dataStream.writeInt(regionX);
        dataStream.writeInt(regionZ);

        boolean[] chunkExistenceBitmap = new boolean[1024];
        for (int i = 0; i < 1024; i++) {
            chunkExistenceBitmap[i] = chunkExists[i];
        }

        writeSerializedExistenceBitmap(dataStream, chunkExistenceBitmap);

        writeNBTFeatures(dataStream);

        byte[][] buckets = new byte[gridSize * gridSize][];
        for (int bx = 0; bx < gridSize; bx++) {
            for (int bz = 0; bz < gridSize; bz++) {
                if (bucketBuffers != null && bucketBuffers[bx * gridSize + bz] != null) {
                    buckets[bx * gridSize + bz] = bucketBuffers[bx * gridSize + bz];
                    continue;
                }
                ByteArrayOutputStream bucketStream = new ByteArrayOutputStream();
                ZstdOutputStream zstdStream = new ZstdOutputStream(bucketStream, this.compressionLevel);
                DataOutputStream bucketDataStream = new DataOutputStream(zstdStream);

                boolean hasData = false;
                for (int cx = 0; cx < 32 / gridSize; cx++) {
                    for (int cz = 0; cz < 32 / gridSize; cz++) {
                        int chunkIndex = (bx * 32 / gridSize + cx) + (bz * 32 / gridSize + cz) * 32;
                        if (this.bufferUncompressedSize[chunkIndex] > 0) {
                            hasData = true;
                            byte[] chunkData = new byte[this.bufferUncompressedSize[chunkIndex]];
                            this.decompressor.decompress(
                                    this.buffer[chunkIndex], 0, chunkData, 0, this.bufferUncompressedSize[chunkIndex]);
                            bucketDataStream.writeInt(chunkData.length + 8);
                            bucketDataStream.writeLong(this.chunkTimestamps[chunkIndex]);
                            bucketDataStream.write(chunkData);
                        } else {
                            bucketDataStream.writeInt(0);
                            bucketDataStream.writeLong(this.chunkTimestamps[chunkIndex]);
                        }
                    }
                }
                bucketDataStream.close();

                if (hasData) {
                    buckets[bx * gridSize + bz] = bucketStream.toByteArray();
                }
            }
        }

        for (int i = 0; i < gridSize * gridSize; i++) {
            dataStream.writeInt(buckets[i] != null ? buckets[i].length : 0);
            dataStream.writeByte(this.compressionLevel);
            long rawHash = 0;
            if (buckets[i] != null) {
                rawHash = LongHashFunction.xx().hashBytes(buckets[i]);
            }
            dataStream.writeLong(rawHash);
        }

        for (int i = 0; i < gridSize * gridSize; i++) {
            if (buckets[i] != null) {
                dataStream.write(buckets[i]);
            }
        }

        dataStream.writeLong(SUPERBLOCK);

        dataStream.flush();
        fileStream.getFD().sync();
        fileStream.getChannel().force(true); // Ensure atomicity on Btrfs
        dataStream.close();

        fileStream.close();
        Files.move(tempFile.toPath(), this.regionFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeNBTFeatures(DataOutputStream dataStream) throws IOException {
        dataStream.writeByte(0); // End of NBT features
    }

    @Override
    public synchronized boolean write() throws IOException {
        if (!read()) {
            return false;
        }

        if (bridge != null && bridge.clear(regionX, regionZ, pendingOrphans)) {
            pendingOrphans.clear();
            markedToSave = false;
            return true;
        }

        boolean allEmpty = true;
        for (boolean exists : chunkExists) {
            if (exists) {
                allEmpty = false;
                break;
            }
        }

        if (allEmpty) {
            Files.deleteIfExists(regionFile);
            getPlugin()
                    .debug(
                            DebugLevel.HIGH,
                            () -> String.format("Deleted region %s with empty header", getIdentifier()));
            return true;
        }

        flush();
        return true;
    }

    @Override
    public boolean exists() {
        return Files.isRegularFile(regionFile);
    }

    @Override
    public @NotNull ChunkInfo getLocalChunk(int localChunkX, int localChunkZ) {
        return new LinearChunk(this, localChunkX, localChunkZ);
    }

    @Override
    public @NotNull Stream<ChunkInfo> getChunks() {
        return IntStream.range(0, TOTAL_CHUNKS).mapToObj(index -> {
            int localChunkX = RegionFile.unpackLocalX(index);
            int localChunkZ = RegionFile.unpackLocalZ(index);
            return getLocalChunk(localChunkX, localChunkZ);
        });
    }

    @Override
    public int getChunksPerRegion() {
        return 1024;
    }

    private class LinearChunk extends ChunkInfo {
        public LinearChunk(@NotNull RegionInfo regionInfo, int localChunkX, int localChunkZ) {
            super(regionInfo, localChunkX, localChunkZ);
        }

        @Override
        public boolean isOrphaned() {
            return isChunkOrphaned(getLocalChunkX(), getLocalChunkZ());
        }

        @Override
        public void setOrphaned() {
            orphanChunk(getLocalChunkX(), getLocalChunkZ());
        }

        @Override
        public long getLastModified() {
            return getChunkLastModified(getLocalChunkX(), getLocalChunkZ());
        }
    }

    private static int getChunkIndex(int x, int z) {
        return (x & 31) + ((z & 31) << 5);
    }

    private static int getTimestamp() {
        return (int) Instant.now().getEpochSecond();
    }

    synchronized boolean isChunkOrphaned(int localChunkX, int localChunkZ) {
        openBucket(localChunkX, localChunkZ);
        int index = getChunkIndex(localChunkX, localChunkZ);
        return !chunkExists[index];
    }

    synchronized long getChunkLastModified(int localChunkX, int localChunkZ) {
        openBucket(localChunkX, localChunkZ);
        return chunkTimestamps[getChunkIndex(localChunkX, localChunkZ)] * 1000L;
    }

    synchronized void orphanChunk(int localChunkX, int localChunkZ) {
        openBucket(localChunkX, localChunkZ);
        int index = getChunkIndex(localChunkX, localChunkZ);
        buffer[index] = null;
        bufferUncompressedSize[index] = 0;
        chunkExists[index] = false;
        pendingOrphans.set(index);
        chunkTimestamps[index] = getTimestamp();
        markToSave();
    }

    private boolean[] deserializeExistenceBitmap(ByteBuffer buffer) {
        boolean[] result = new boolean[1024];
        for (int i = 0; i < 128; i++) {
            byte b = buffer.get();
            for (int j = 0; j < 8; j++) {
                result[i * 8 + j] = ((b >> (7 - j)) & 1) == 1;
            }
        }
        return result;
    }

    private void writeSerializedExistenceBitmap(DataOutputStream out, boolean[] bitmap) throws IOException {
        for (int i = 0; i < 128; i++) {
            byte b = 0;
            for (int j = 0; j < 8; j++) {
                if (bitmap[i * 8 + j]) {
                    b |= (byte) (1 << (7 - j));
                }
            }
            out.writeByte(b);
        }
    }
}
