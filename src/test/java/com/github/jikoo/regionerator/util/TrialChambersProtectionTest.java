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
package com.github.jikoo.regionerator.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class TrialChambersProtectionTest {
    private static final String TRIAL_CHAMBERS = "minecraft:trial_chambers";

    @Test
    void protectsStructureStart() throws Exception {
        CompoundTag starts = new CompoundTag();
        CompoundTag start = new CompoundTag();
        start.putString("id", TRIAL_CHAMBERS);
        starts.put(TRIAL_CHAMBERS, start);
        assertTrue(TrialChambersProtection.containsTrialChambers(chunk("starts", starts)));
    }

    @Test
    void protectsReferencedChunksWithoutStructureStart() throws Exception {
        CompoundTag references = new CompoundTag();
        // Packed chunk coordinates (0, 0) are zero, but still a valid structure reference.
        references.putLongArray(TRIAL_CHAMBERS, new long[] {0L});
        assertTrue(TrialChambersProtection.containsTrialChambers(chunk("References", references)));
    }

    @Test
    void emptyReferenceArrayDoesNotProtectChunk() throws Exception {
        CompoundTag references = new CompoundTag();
        references.putLongArray(TRIAL_CHAMBERS, new long[0]);
        assertFalse(TrialChambersProtection.containsTrialChambers(chunk("References", references)));
    }

    @Test
    void invalidStartDoesNotProtectChunk() throws Exception {
        CompoundTag starts = new CompoundTag();
        CompoundTag start = new CompoundTag();
        start.putString("id", "INVALID");
        starts.put(TRIAL_CHAMBERS, start);
        assertFalse(TrialChambersProtection.containsTrialChambers(chunk("starts", starts)));
    }

    @Test
    void unrelatedStructuresDoNotProtectChunk() throws Exception {
        CompoundTag references = new CompoundTag();
        references.putLongArray("minecraft:ancient_city", new long[] {0L});
        assertFalse(TrialChambersProtection.containsTrialChambers(chunk("References", references)));
        assertFalse(TrialChambersProtection.containsTrialChambers(new CompoundTag()));
    }

    @Test
    void malformedStructureDataDoesNotReportUnprotected() {
        CompoundTag chunk = new CompoundTag();
        chunk.putString("structures", "corrupted");
        assertThrows(IOException.class, () -> TrialChambersProtection.containsTrialChambers(chunk));

        CompoundTag references = new CompoundTag();
        references.putString(TRIAL_CHAMBERS, "corrupted");
        assertThrows(
                IOException.class,
                () -> TrialChambersProtection.containsTrialChambers(chunk("References", references)));

        CompoundTag starts = new CompoundTag();
        starts.put(TRIAL_CHAMBERS, new CompoundTag());
        assertThrows(IOException.class, () -> TrialChambersProtection.containsTrialChambers(chunk("starts", starts)));
    }

    @Test
    void queryReadsStructureReferencesAndReleasesRead() throws Exception {
        CompoundTag references = new CompoundTag();
        references.putLongArray(TRIAL_CHAMBERS, new long[] {0L});
        AtomicBoolean cancelled = new AtomicBoolean();
        assertTrue(TrialChambersProtection.query(
                callback -> {
                    callback.accept(chunk("References", references), null);
                    return () -> cancelled.compareAndSet(false, true);
                },
                1,
                TimeUnit.SECONDS));
        assertTrue(cancelled.get());
    }

    @Test
    void missingChunkDataDoesNotReportUnprotected() {
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> TrialChambersProtection.query(
                        callback -> {
                            callback.accept(null, null);
                            return () -> true;
                        },
                        1,
                        TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, failure.getCause());
    }

    @Test
    void readFailureDoesNotReportUnprotected() {
        IOException readFailure = new IOException("Unable to read region");
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> TrialChambersProtection.query(
                        callback -> {
                            callback.accept(null, readFailure);
                            return () -> true;
                        },
                        1,
                        TimeUnit.SECONDS));
        assertSame(readFailure, failure.getCause());
    }

    @Test
    void timedOutReadIsCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        assertThrows(
                TimeoutException.class,
                () -> TrialChambersProtection.query(
                        callback -> () -> cancelled.compareAndSet(false, true), 0, TimeUnit.NANOSECONDS));
        assertTrue(cancelled.get());
    }

    private static CompoundTag chunk(String key, CompoundTag value) {
        CompoundTag chunk = new CompoundTag();
        CompoundTag structures = new CompoundTag();
        structures.put(key, value);
        chunk.put("structures", structures);
        return chunk;
    }
}
