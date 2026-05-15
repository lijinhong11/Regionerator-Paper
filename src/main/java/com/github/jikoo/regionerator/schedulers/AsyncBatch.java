package com.github.jikoo.regionerator.schedulers;

import java.util.concurrent.TimeUnit;

import com.github.jikoo.regionerator.Regionerator;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.jetbrains.annotations.NotNull;

public abstract class AsyncBatch<T> extends Batch<T> {

  protected AsyncBatch(
      @NotNull Regionerator plugin,
      long gatherPeriod,
      @NotNull TimeUnit gatherUnit) {
    super(plugin, gatherPeriod, gatherUnit);
  }

  @Override
  @NotNull WrappedTask schedule(@NotNull Runnable runnable) {
    return this.plugin.getScheduler().runLaterAsync(runnable, this.gatherTicks);
  }
}
