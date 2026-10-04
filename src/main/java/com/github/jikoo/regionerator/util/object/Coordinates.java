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
package com.github.jikoo.regionerator.util.object;

public class Coordinates {
    public static int blockToChunk(final int block) {
        return block >> 4;
    }

    public static int chunkToBlock(final int chunk) {
        return chunk << 4;
    }

    public static int chunkToRegion(final int chunk) {
        return chunk >> 5;
    }

    public static int regionToChunk(final int region) {
        return region << 5;
    }
}
