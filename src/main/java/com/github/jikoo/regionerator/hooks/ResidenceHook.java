/*
 * Copyright (c) 2015-2026 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.hooks;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.github.jikoo.regionerator.world.DummyChunk;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * PluginHook for <a href="https://www.spigotmc.org/resources/residence.11480/">Residence</a>.
 */
public class ResidenceHook extends PluginHook {

	public ResidenceHook() {
		super("Residence");
	}

	@Override
	public boolean isChunkProtected(@NotNull World chunkWorld, int chunkX, int chunkZ) {
		try {
			List<ClaimedResidence> residences = Residence.getInstance().getResidenceManager()
					.getByChunk(new DummyChunk(chunkWorld, chunkX, chunkZ));
			return residences != null && !residences.isEmpty();
		} catch (RuntimeException e) {
			// Fail closed. If Residence cannot answer safely, do not delete the chunk.
			return true;
		}
	}

	@Override
	public boolean isAsyncCapable() {
		return true;
	}

}
