package com.ascensionlib.web;

import com.ascensionlib.scout.CaptureRevealPayload;
import java.util.ArrayDeque;
import java.util.Deque;

/** One player's committed captures: one active GUI card, a bounded backlog, and delivery deduplication. */
final class CaptureRevealQueue {
    private final CaptureRevealHistory history = new CaptureRevealHistory();
    private final Deque<CaptureRevealPayload> waiting = new ArrayDeque<>();
    private CaptureRevealPayload active;

    boolean offer(CaptureRevealPayload reveal) {
        if (history.contains(reveal.revealId())) return true;
        if (waiting.size() >= 8) return false;
        waiting.add(reveal);
        history.remember(reveal.revealId());
        return true;
    }

    CaptureRevealPayload active() { return active; }
    boolean hasWaiting() { return !waiting.isEmpty(); }
    CaptureRevealPayload advance() {
        if (active == null) active = waiting.poll();
        return active;
    }
    boolean dismiss(String revealId) {
        if (active == null || !active.revealId().equals(revealId)) return false;
        active = null;
        return true;
    }
    void clear() { active = null; waiting.clear(); }
}
