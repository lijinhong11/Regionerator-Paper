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
package com.github.jikoo.regionerator.util.object;

@FunctionalInterface
public interface ThrowableTriFunction<T1, T2, T3, R, E extends Throwable> {
    R apply(T1 t1, T2 t2, T3 t3) throws E;
}
