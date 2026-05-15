/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.listeners;

import com.github.jikoo.planarwrappers.scheduler.TickTimeUnit;
import com.github.jikoo.planarwrappers.util.Coords;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.schedulers.AsyncBatch;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Listener used to flag chunks as visited.
 */
@SuppressWarnings("unchecked")
public class FlaggingListener implements Listener {

	private final @NotNull Regionerator plugin;
	private final @NotNull FlaggingRunnable flagger;
	private final @NotNull AsyncBatch<ChunkId> chunkPopulateBatch;

	public FlaggingListener(@NotNull Regionerator plugin) {
		this.plugin = plugin;
		this.flagger = new FlaggingRunnable(plugin);

		for (Player player : plugin.getServer().getOnlinePlayers()) {
			flagger.add(player);
		}

		flagger.schedule(plugin);

		this.chunkPopulateBatch = new AsyncBatch<>(this.plugin, 2L, TimeUnit.SECONDS) {
			@Override
			public void post(@NotNull @UnmodifiableView Set<ChunkId> batch) {
				for (ChunkId chunkId : batch) {
					plugin.getFlagger().flagChunk(
									chunkId.worldName,
									chunkId.chunkX,
									chunkId.chunkZ,
									plugin.config().getFlagGenerated(chunkId.worldName));
				}
			}
		};
	}

	public void cancel() {
		this.flagger.cancel();
		this.chunkPopulateBatch.purge();
	}

	/**
	 * Regionerator periodically spends a sizable amount of time deleting untouched area, causing unnecessary load.
	 * To combat this a little, freshly generated chunks are automatically flagged.
	 * 
	 * @param event the ChunkPopulateEvent
	 */
	@EventHandler
	private void onChunkPopulate(@NotNull ChunkPopulateEvent event) {
		if (!plugin.config().isEnabled(event.getWorld().getName())) {
			return;
		}

		this.chunkPopulateBatch.add(new ChunkId(event.getChunk()));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerJoin(@NotNull PlayerJoinEvent event) {
		flagger.add(event.getPlayer());
	}

	@EventHandler
	private void onPlayerQuit(@NotNull PlayerQuitEvent event) {
		flagger.remove(event.getPlayer());
	}

	/**
	 * DistributedTask for periodically marking chunks near players as visited.
	 */
	private static class FlaggingRunnable implements Consumer<WrappedTask> {
		private final Set<Player> allContent = new HashSet<>();
		private final @NotNull Set<Player> @NotNull [] distributedContent;
		private final @NotNull Consumer<Collection<Player>> consumer;
		private WrappedTask taskInstance;
		private int currentIndex = 0;

		FlaggingRunnable(@NotNull Regionerator plugin) {
			long period = plugin.config().getFlaggingInterval() * 50;
			int totalTicks = (int) TickTimeUnit.toTicks(period, TimeUnit.MILLISECONDS);
			if (totalTicks < 2) {
				throw new IllegalArgumentException("Period must be 2 ticks or greater");
			} else {
				distributedContent = (Set[]) Array.newInstance(this.allContent.getClass(), totalTicks);

				for(int index = 0; index < this.distributedContent.length; ++index) {
					this.distributedContent[index] = new HashSet<>();
				}
            }

			consumer = players -> {
				List<ChunkId> flagged = new ArrayList<>();
				for (Player player : players) {
					if (player.getGameMode().name().equals("SPECTATOR")
							|| !plugin.config().isEnabled(player.getWorld().getName())) {
						continue;
					}

					flagged.add(new ChunkId(player.getWorld(), player.getLocation()));
				}

				if (!flagged.isEmpty()) {
					plugin.getScheduler().runAsync(t -> {
						for (ChunkId chunk : flagged) {
							plugin.getFlagger().flagChunksInRadius(chunk.worldName, chunk.chunkX, chunk.chunkZ);
						}
					});
				}
			};
		}

		public void add(@NotNull Player content) {
			if (this.allContent.add(content)) {
				int lowestSize = Integer.MAX_VALUE;
				int lowestIndex = 0;

				for(int index = 0; index < this.distributedContent.length; ++index) {
					int size = this.distributedContent[index].size();
					if (size < lowestSize) {
						lowestSize = size;
						lowestIndex = index;
					}
				}

				this.distributedContent[lowestIndex].add(content);
			}
		}

		public void remove(@NotNull Player content) {
			if (this.allContent.remove(content)) {
				for(Set<Player> contentPartition : this.distributedContent) {
					if (contentPartition.remove(content)) {
						break;
					}
				}
			}

		}

		@Override
		public void accept(WrappedTask task) {
			this.taskInstance = task;
			this.consumer.accept(Collections.unmodifiableSet(this.distributedContent[this.currentIndex]));
			++this.currentIndex;
			if (this.currentIndex >= this.distributedContent.length) {
				this.currentIndex = 0;
			}
		}

		public void schedule(@NotNull Regionerator plugin) {
			if (this.taskInstance != null) {
				this.taskInstance.cancel();
			}

			plugin.getScheduler().runTimer(this, 1L, 1L);
		}

		public void cancel() {
			if (this.taskInstance != null) {
				this.taskInstance.cancel();
			}
		}
	}

	private static class ChunkId {
		private final @NotNull String worldName;
		private final int chunkX;
		private final int chunkZ;

		private ChunkId(@NotNull Chunk chunk) {
			this.worldName = chunk.getWorld().getName();
			this.chunkX = chunk.getX();
			this.chunkZ = chunk.getZ();
		}

		private ChunkId(@NotNull World world, @NotNull Location location) {
			this.worldName = world.getName();
			this.chunkX = Coords.blockToChunk(location.getBlockX());
			this.chunkZ = Coords.blockToChunk(location.getBlockZ());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ChunkId chunkId = (ChunkId) o;
			return chunkX == chunkId.chunkX && chunkZ == chunkId.chunkZ && worldName.equals(chunkId.worldName);
		}

		@Override
		public int hashCode() {
			return Objects.hash(worldName, chunkX, chunkZ);
		}

	}

}
