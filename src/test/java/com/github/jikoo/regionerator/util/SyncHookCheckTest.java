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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class SyncHookCheckTest {
    @Test
    void backgroundWorkerWaitsForScheduledProtectionResult() throws Exception {
        CompletableFuture<Runnable> scheduled = new CompletableFuture<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Thread serverThread = Thread.currentThread();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(() -> SyncHookCheck.query(
                    () -> true,
                    () -> {
                        assertSame(serverThread, Thread.currentThread());
                        return true;
                    },
                    callback -> {
                        scheduled.complete(callback);
                        return () -> cancelled.set(true);
                    },
                    Assertions::fail,
                    5,
                    TimeUnit.SECONDS));

            Runnable callback = scheduled.get(2, TimeUnit.SECONDS);
            assertFalse(result.isDone(), "Pending checks must not fall through to deletion eligibility");
            callback.run();
            assertEquals(VisitStatus.PROTECTED, result.get(2, TimeUnit.SECONDS));
            assertTrue(cancelled.get());
        }
    }

    @Test
    void completedUnprotectedCheckAllowsFurtherVisitStatusChecks() {
        assertEquals(VisitStatus.UNVISITED, immediate(() -> true, () -> false));
        assertEquals(VisitStatus.PROTECTED, immediate(() -> true, () -> true));
    }

    @Test
    void hookExceptionFailsClosed() {
        AtomicReference<Exception> failure = new AtomicReference<>();
        VisitStatus status = SyncHookCheck.query(
                () -> true,
                () -> {
                    throw new IllegalStateException("hook failed");
                },
                callback -> {
                    callback.run();
                    return () -> {};
                },
                failure::set,
                1,
                TimeUnit.SECONDS);
        assertEquals(VisitStatus.UNKNOWN, status);
        assertInstanceOf(IllegalStateException.class, failure.get().getCause());
    }

    @Test
    void rejectedSchedulingFailsClosed() {
        AtomicReference<Exception> failure = new AtomicReference<>();
        VisitStatus status = SyncHookCheck.query(
                () -> true,
                () -> fail("Must not run"),
                callback -> {
                    throw new IllegalStateException("scheduler stopped");
                },
                failure::set,
                1,
                TimeUnit.SECONDS);
        assertEquals(VisitStatus.UNKNOWN, status);
        assertInstanceOf(IllegalStateException.class, failure.get());
    }

    @Test
    void timeoutCancelsTaskAndIgnoresLateCallback() {
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Exception> failure = new AtomicReference<>();
        VisitStatus status = SyncHookCheck.query(
                () -> true,
                () -> fail("Timed out hook must not run"),
                callback -> {
                    scheduled.set(callback);
                    return () -> cancelled.set(true);
                },
                failure::set,
                0,
                TimeUnit.NANOSECONDS);
        assertEquals(VisitStatus.UNKNOWN, status);
        assertInstanceOf(java.util.concurrent.TimeoutException.class, failure.get());
        assertTrue(cancelled.get());
        scheduled.get().run();
    }

    @Test
    void disabledPluginDoesNotScheduleOrCheckHooks() {
        assertEquals(
                VisitStatus.UNKNOWN,
                SyncHookCheck.query(
                        () -> false,
                        () -> fail("Must not check"),
                        callback -> fail("Must not schedule"),
                        failure -> fail(failure),
                        1,
                        TimeUnit.SECONDS));

        AtomicBoolean enabled = new AtomicBoolean(true);
        assertEquals(
                VisitStatus.UNKNOWN,
                SyncHookCheck.query(
                        enabled::get,
                        () -> fail("Disabled hook must not run"),
                        callback -> {
                            enabled.set(false);
                            callback.run();
                            return () -> {};
                        },
                        failure -> fail(failure),
                        1,
                        TimeUnit.SECONDS));
    }

    @Test
    void disableDuringCheckDoesNotReportUnprotected() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        assertEquals(VisitStatus.UNKNOWN, immediate(enabled::get, () -> {
            enabled.set(false);
            return false;
        }));
    }

    @Test
    void interruptionIsPreservedAndFailsClosed() throws Exception {
        CountDownLatch scheduled = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<VisitStatus> status = new CompletableFuture<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            status.complete(SyncHookCheck.query(
                    () -> true,
                    () -> fail("Must not check"),
                    callback -> {
                        scheduled.countDown();
                        return () -> cancelled.set(true);
                    },
                    failure -> fail(failure),
                    5,
                    TimeUnit.SECONDS));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        worker.start();
        try {
            assertTrue(scheduled.await(2, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(2000);
            assertFalse(worker.isAlive());
            assertEquals(VisitStatus.UNKNOWN, status.get(2, TimeUnit.SECONDS));
            assertTrue(interrupted.get());
            assertTrue(cancelled.get());
        } finally {
            worker.interrupt();
            worker.join(2000);
        }
    }

    private static VisitStatus immediate(BooleanSupplier enabled, BooleanSupplier protectedChunk) {
        return SyncHookCheck.query(
                enabled,
                protectedChunk,
                callback -> {
                    callback.run();
                    return () -> {};
                },
                failure -> fail(failure),
                1,
                TimeUnit.SECONDS);
    }
}
