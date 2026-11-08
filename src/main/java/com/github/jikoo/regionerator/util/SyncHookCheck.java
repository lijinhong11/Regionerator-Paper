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

import com.github.jikoo.regionerator.VisitStatus;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Waits for a scheduled protection check from a background thread only.
 * UNVISITED means all hooks completed without protection, not that deletion is allowed.
 * Every incomplete or failed check returns UNKNOWN so the caller skips deletion.
 */
//TODO: support ProtectorAPI in the future
final class SyncHookCheck {
    private SyncHookCheck() {}

    static VisitStatus query(
            BooleanSupplier enabled,
            BooleanSupplier protectedChunk,
            Function<Runnable, Runnable> schedule,
            Consumer<Exception> onFailure,
            long timeout,
            TimeUnit unit) {
        if (!enabled.getAsBoolean()) {
            return VisitStatus.UNKNOWN;
        }

        CompletableFuture<VisitStatus> result = new CompletableFuture<>();
        Runnable cancelTask = null;
        try {
            cancelTask = schedule.apply(() -> {
                if (result.isDone()) {
                    return;
                }
                try {
                    if (!enabled.getAsBoolean()) {
                        result.complete(VisitStatus.UNKNOWN);
                        return;
                    }
                    result.complete(protectedChunk.getAsBoolean() ? VisitStatus.PROTECTED : VisitStatus.UNVISITED);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
            VisitStatus status = result.get(timeout, unit);
            return enabled.getAsBoolean() ? status : VisitStatus.UNKNOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return VisitStatus.UNKNOWN;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            if (enabled.getAsBoolean()) {
                onFailure.accept(e);
            }
            return VisitStatus.UNKNOWN;
        } finally {
            // A disabled plugin or unavailable scheduler must not leave a late check running.
            result.cancel(false);
            if (cancelTask != null) {
                cancelTask.run();
            }
        }
    }
}
