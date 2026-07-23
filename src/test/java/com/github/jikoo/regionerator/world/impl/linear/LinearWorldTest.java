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
package com.github.jikoo.regionerator.world.impl.linear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LinearWorldTest {

    @Test
    void keepsSourceDirectoryWhenDeduplicatingRegions() {
        assertEquals(
                List.of("entities/r.194.194.linear"),
                LinearWorld.distinctRegionPaths(List.of("entities/r.194.194.linear", "poi/r.194.194.linear")));
    }
}
