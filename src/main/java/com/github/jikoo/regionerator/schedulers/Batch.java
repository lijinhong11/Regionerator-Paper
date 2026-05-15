package com.github.jikoo.regionerator.schedulers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.github.jikoo.planarwrappers.scheduler.TickTimeUnit;
import com.github.jikoo.regionerator.Regionerator;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

/**
 * Aggregate multiple elements for handling later. Helps reduce redundant runnable/thread creation.
 *
 * @param <T>
 */
public abstract class Batch<T> {

  private final @NotNull Set<T> elements = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final @NotNull AtomicReference<WrappedTask> task = new AtomicReference<>();
  final @NotNull Regionerator plugin;
  final long gatherTicks;

  protected Batch(@NotNull Regionerator plugin, long gatherPeriod, @NotNull TimeUnit gatherUnit) {
    this.plugin = plugin;
    this.gatherTicks = TickTimeUnit.toTicks(gatherPeriod, gatherUnit);
    if (gatherTicks <= 0) {
      throw new IllegalArgumentException("Gather ticks must be > 0");
    }
  }

  public void add(@NotNull T element) {
    this.elements.add(element);
    this.trySchedule();
  }

  private void trySchedule() {
    if (this.task.get() != null || this.elements.isEmpty()) {
      return;
    }

    this.task.set(this.schedule(this::run));
  }

  abstract @NotNull WrappedTask schedule(@NotNull Runnable runnable);

  private void run() {
    // Copy all elements to a new set, clearing original in the process.
    Set<T> localBatch = new HashSet<>(this.elements.size());
    this.elements.removeIf(element -> {
      localBatch.add(element);
      return true;
    });

    // Unset task.
    this.task.set(null);

    // Ensure we don't miss new elements added during time between clearing and unsetting.
    if (!this.elements.isEmpty()) {
      this.trySchedule();
    }

    // Post results to the batch consumer.
    this.post(Collections.unmodifiableSet(localBatch));
  }

  protected abstract void post(@NotNull @UnmodifiableView Set<T> batch);

  public void purge() {
    if (elements.isEmpty()) {
      return;
    }

    var batchCopy = Set.copyOf(this.elements);
    this.task.getAndUpdate(internalTask -> {
      if (internalTask != null && !internalTask.isCancelled()) {
        internalTask.cancel();
      }
      return null;
    });
    this.elements.clear();

    this.post(batchCopy);
  }

}
