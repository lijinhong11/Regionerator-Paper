package com.github.jikoo.regionerator.listeners;

import com.github.jikoo.regionerator.activity.ChunkActivityTracker;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class ChunkActivityListener implements Listener {
    private final ChunkActivityTracker tracker;

    public ChunkActivityListener(ChunkActivityTracker tracker) {
        this.tracker = tracker;
    }

    /* entity events */

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        tracker.recordActivity(e.getTo().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent e) {
        tracker.recordActivity(e.getTo().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(EntityInteractEvent e) {
        tracker.recordActivity(e.getBlock().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        tracker.recordActivity(e.getEntity().getChunk());
    }

    /* block events */

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Chunk chunk;

        if (event.getClickedBlock() == null) {
            chunk = event.getPlayer().getChunk();
        } else {
            chunk = event.getClickedBlock().getChunk();
        }

        tracker.recordActivity(chunk);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        tracker.recordActivity(event.getBlock().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        tracker.recordActivity(event.getBlock().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        tracker.recordActivity(e.getBlock().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonEvent e) {
        tracker.recordActivity(e.getBlock().getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        tracker.recordActivity(e.getBlock().getChunk());
    }
}
