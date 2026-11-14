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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.*;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

/*
 * Upstream branch: ver/26.3<br>
 * Upstream commit: 6e7e3db32bb6c1ad686b5dc14e62e8e3ad017662<br>
 */

/**
 * <b>Source is adapted from LophineLabs/Lophine</b><br>
 * Thanks to their effort!
 * <p>
 * Edited by lijinhong11
 */
public class BufferedLinearRegionFileFlusher implements Runnable {
    private static final Logger logger = LogUtils.getLogger();

    private final Set<BufferedLinearRegionFile> inManagement = new ObjectArraySet<>();
    private final ScheduledFuture<?> flusherChecker;
    private final Executor ioWorkerPool;
    private final long flushOfWriteTimeoutMs;

    BufferedLinearRegionFileFlusher(int nIoThreads, long checkIntervalMs, long flushOfWriteTimeoutMs) {
        Validate.isTrue(nIoThreads > 0, "Number of I/O threads must > 0!");
        Validate.isTrue(checkIntervalMs > 0, "Check interval must > 0");
        Validate.isTrue(flushOfWriteTimeoutMs > 0, "Flush of write timeout must > 0");

        this.ioWorkerPool = Executors.newFixedThreadPool(
                nIoThreads,
                new ThreadFactoryBuilder()
                        .setNameFormat("BufferedLinearRegionFile I/O Worker %d")
                        .setDaemon(true)
                        .build());
        this.flusherChecker = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                        .setNameFormat("BufferedLinearRegionFile Flusher Checker")
                        .setDaemon(true)
                        .build())
                .scheduleWithFixedDelay(this, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        this.flushOfWriteTimeoutMs = flushOfWriteTimeoutMs;
    }

    public void shutdown() {
        this.flusherChecker.cancel(false);

        this.forceFlushAll();

        ((ExecutorService) this.ioWorkerPool).shutdown();
        boolean interrupted = false;
        for (; ; ) {
            try {
                if (((ExecutorService) this.ioWorkerPool).awaitTermination(100, TimeUnit.MILLISECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        // Regionerator owns these handles, unlike the server's region file cache.
        final BufferedLinearRegionFile[] files;
        synchronized (this) {
            files = this.inManagement.toArray(new BufferedLinearRegionFile[0]);
        }
        try {
            for (BufferedLinearRegionFile file : files) {
                try {
                    file.close();
                } catch (IOException e) {
                    logger.error("Failed to close buffered linear region file", e);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void forceFlushAll() {
        final BufferedLinearRegionFile[] copied;
        synchronized (this) {
            copied = this.inManagement.toArray(new BufferedLinearRegionFile[0]);
        }

        for (BufferedLinearRegionFile file : copied) {
            if (!file.readLock(false)) {
                continue;
            }

            boolean closed;
            try {
                closed = file.isClosedRaw();
            } finally {
                file.releaseReadLock();
            }

            if (closed || !file.shouldSync()) {
                continue;
            }

            this.ioWorkerPool.execute(() -> {
                try {
                    file.syncIfNeeded();
                } catch (IOException e) {
                    logger.error("Failed to sync master file: ", e);
                }
            });
        }
    }

    @Override
    public void run() {
        final long currentNanos = System.nanoTime();
        final BufferedLinearRegionFile[] copied;

        synchronized (this) {
            copied = this.inManagement.toArray(new BufferedLinearRegionFile[0]);
        }

        for (BufferedLinearRegionFile file : copied) {
            // try acquiring the read lock
            if (!file.readLock(true)) {
                // if the read lock is unacquirable, it might mean there is another operations is processing(might be a
                // writing operation)
                continue;
            }

            boolean closed;

            try {
                // check if the file is closed
                closed = file.isClosedRaw();
            } finally {
                file.releaseReadLock();
            }

            if (closed) {
                // Closed files unregister themselves in markClosed().
                continue;
            }

            // skip non sync-required files
            if (!file.shouldSync()) {
                continue;
            }

            final long lastWriteNanos = file.getLastWritten();
            final long timeElapsed = (currentNanos - lastWriteNanos) / 1_000_000; // Convert to milliseconds

            // if deadline(timeout) reached
            if (timeElapsed >= this.flushOfWriteTimeoutMs) {
                // already marked to flush
                if (!file.markAsBeingSynced()) {
                    continue;
                }

                this.ioWorkerPool.execute(() -> {
                    try {
                        file.syncIfNeeded();
                    } catch (IOException e) {
                        logger.error("Failed to sync master file: ", e);
                    }
                });
            }
        }
    }

    public void removeFile(BufferedLinearRegionFile fileToRemove) {
        synchronized (this) {
            this.inManagement.remove(fileToRemove);
        }
    }

    public void addFile(BufferedLinearRegionFile fileToAdd) {
        synchronized (this) {
            this.inManagement.add(fileToAdd);
        }
    }
}
