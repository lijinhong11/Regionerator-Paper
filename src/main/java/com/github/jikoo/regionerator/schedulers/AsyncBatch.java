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
package com.github.jikoo.regionerator.schedulers;

import com.github.jikoo.regionerator.Regionerator;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public abstract class AsyncBatch<T> extends Batch<T> {

    protected AsyncBatch(@NotNull Regionerator plugin, long gatherPeriod, @NotNull TimeUnit gatherUnit) {
        super(plugin, gatherPeriod, gatherUnit);
    }

    @Override
    @NotNull
    WrappedTask schedule(@NotNull Runnable runnable) {
        return this.plugin.getScheduler().runLaterAsync(runnable, this.gatherTicks);
    }
}
