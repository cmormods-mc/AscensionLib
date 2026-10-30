package com.ascensionlib.web;

import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded, per-player delivery history: one committed acquisition must not create two reveal cards. */
final class CaptureRevealHistory {
    private static final int LIMIT = 256;
    private final Set<String> delivered = new LinkedHashSet<>();

    boolean contains(String revealId) { return delivered.contains(revealId); }

    void remember(String revealId) {
        delivered.add(revealId);
        if (delivered.size() > LIMIT) delivered.remove(delivered.iterator().next());
    }
}
