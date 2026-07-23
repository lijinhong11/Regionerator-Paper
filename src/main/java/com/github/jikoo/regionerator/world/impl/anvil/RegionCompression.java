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
package com.github.jikoo.regionerator.world.impl.anvil;

import com.github.jikoo.planarwrappers.function.ThrowingFunction;
import java.io.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

enum RegionCompression {
    GZIP(
            (in) -> new BufferedInputStream(new GZIPInputStream(in)),
            (out) -> new BufferedOutputStream(new GZIPOutputStream(out))),
    ZLIB(
            (in) -> new BufferedInputStream(new InflaterInputStream(in)),
            (out) -> new BufferedOutputStream(new DeflaterOutputStream(out))),
    NONE((in) -> in, (out) -> out);

    private final ThrowingFunction<InputStream, InputStream, IOException> decodeIn;
    private final ThrowingFunction<OutputStream, OutputStream, IOException> encodeOut;

    RegionCompression(
            ThrowingFunction<InputStream, InputStream, IOException> decodeIn,
            ThrowingFunction<OutputStream, OutputStream, IOException> encodeOut) {
        this.decodeIn = decodeIn;
        this.encodeOut = encodeOut;
    }

    @NotNull
    InputStream decode(@NotNull InputStream stream) throws IOException {
        return decodeIn.apply(stream);
    }

    @NotNull
    OutputStream encode(@NotNull OutputStream stream) throws IOException {
        return encodeOut.apply(stream);
    }

    int getCompressionId() {
        return ordinal() + 1;
    }

    static boolean isSupportedCompressionId(int schemaId) {
        return 0 < schemaId && schemaId <= values().length;
    }

    static @Nullable RegionCompression byCompressionId(int schemaId) {
        if (!isSupportedCompressionId(schemaId)) {
            return null;
        }
        return values()[schemaId - 1];
    }
}
