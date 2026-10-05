package com.cobbleascend.domain.v1;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Level milestones are every tenth level through 100. */
public final class Milestones {
    public static final int STEP = 10;
    public static final int MAX_LEVEL = 100;

    private Milestones() {}

    public static boolean isMilestone(int level) {
        return level >= STEP && level <= MAX_LEVEL && level % STEP == 0;
    }

    /** Milestones reached at {@code level}; levels above 100 clamp, levels below 1 are rejected. */
    public static Set<Integer> reachedThrough(int level) {
        if (level < 1) throw new IllegalArgumentException("Level must be at least 1");
        var result = new TreeSet<Integer>();
        for (int m = STEP; m <= Math.min(level, MAX_LEVEL); m += STEP) result.add(m);
        return Collections.unmodifiableSet(result);
    }
}
