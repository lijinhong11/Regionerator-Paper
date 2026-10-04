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

import ca.spottedleaf.moonrise.patches.chunk_system.storage.ChunkSystemRegionFile;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

public interface BufferedRegionFile extends ChunkSystemRegionFile, AutoCloseable {
    Path getPath();

    DataInputStream getChunkDataInputStream(ChunkPos pos) throws IOException;

    boolean doesChunkExist(ChunkPos pos) throws Exception;

    DataOutputStream getChunkDataOutputStream(ChunkPos pos) throws IOException;

    void flush() throws IOException;

    void clear(ChunkPos pos) throws IOException;

    boolean hasChunk(ChunkPos pos);

    void close() throws IOException;

    void write(ChunkPos pos, ByteBuffer buf) throws IOException;

    CompoundTag getOversizedData(int x, int z) throws IOException;

    boolean isOversized(int x, int z);

    boolean recalculateHeader() throws IOException;

    void setOversized(int x, int z, boolean oversized) throws IOException;

    default int getRecalculateCount() {
        return 0;
    } // Anonymous - Configurable region file format
}
