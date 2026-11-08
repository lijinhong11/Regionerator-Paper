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

import com.github.jikoo.regionerator.VisitStatus;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class VisitStatusCacheTest {
    @Test
    void concurrentQueryDoesNotWaitForInFlightCalculation() throws Exception {
        CountDownLatch calculating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        VisitStatusCache cache = new VisitStatusCache(
                () -> {
                    calculating.countDown();
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    return VisitStatus.PROTECTED;
                },
                1);

        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(cache::get);
            try {
                assertTrue(calculating.await(2, TimeUnit.SECONDS));
                assertEquals(VisitStatus.UNKNOWN, cache.get());
            } finally {
                release.countDown();
            }
            assertEquals(VisitStatus.PROTECTED, result.get(2, TimeUnit.SECONDS));
            assertEquals(VisitStatus.PROTECTED, cache.get());
        }
    }

    @Test
    void reentrantHookQueryFailsClosedWithoutOverwritingFinalStatus() {
        AtomicReference<VisitStatusCache> reference = new AtomicReference<>();
        VisitStatusCache cache = new VisitStatusCache(
                () -> {
                    assertEquals(VisitStatus.UNKNOWN, reference.get().get());
                    return VisitStatus.PROTECTED;
                },
                1);
        reference.set(cache);
        assertEquals(VisitStatus.PROTECTED, cache.get());
        assertEquals(VisitStatus.PROTECTED, cache.get());
    }

    @Test
    void failedCalculationReleasesGuardAndSuccessfulResultIsCached() {
        AtomicInteger calls = new AtomicInteger();
        VisitStatusCache cache = new VisitStatusCache(
                () -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("hook failed");
                    }
                    return VisitStatus.PROTECTED;
                },
                1);
        assertThrows(IllegalStateException.class, cache::get);
        assertEquals(VisitStatus.PROTECTED, cache.get());
        assertEquals(VisitStatus.PROTECTED, cache.get());
        assertEquals(2, calls.get());
    }

    @Test
    void unknownStatusIsNotCached() {
        AtomicInteger calls = new AtomicInteger();
        VisitStatusCache cache = new VisitStatusCache(
                () -> calls.incrementAndGet() == 1 ? VisitStatus.UNKNOWN : VisitStatus.PROTECTED, 1);

        assertEquals(VisitStatus.UNKNOWN, cache.get());
        assertEquals(VisitStatus.PROTECTED, cache.get());
        assertEquals(VisitStatus.PROTECTED, cache.get());
        assertEquals(2, calls.get());
    }
}
