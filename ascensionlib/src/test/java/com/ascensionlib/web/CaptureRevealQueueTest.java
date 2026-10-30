package com.ascensionlib.web;

import static org.junit.jupiter.api.Assertions.*;
import com.ascensionlib.scout.CaptureRevealPayload;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptureRevealQueueTest {
    private CaptureRevealPayload reveal(String id) { return new CaptureRevealPayload(id, "Pangoro", "cobblemon:pangoro", List.of(), null, id); }

    @Test void duplicatedActiveAndWaitingCapturesCannotCreateAnotherCard() {
        var queue = new CaptureRevealQueue();
        queue.offer(reveal("first")); queue.offer(reveal("first"));
        assertEquals("first", queue.advance().revealId());
        queue.offer(reveal("first")); queue.offer(reveal("second")); queue.offer(reveal("second"));
        assertEquals("first", queue.advance().revealId(), "opening cannot replace an active card");
        assertTrue(queue.dismiss("first"));
        assertEquals("second", queue.advance().revealId());
        assertTrue(queue.dismiss("second")); assertNull(queue.advance());
    }
    @Test void aLateDismissalCannotCloseTheNextCard() {
        var queue = new CaptureRevealQueue();
        queue.offer(reveal("first")); queue.advance(); queue.dismiss("first");
        queue.offer(reveal("second")); queue.advance();
        assertFalse(queue.dismiss("first")); assertEquals("second", queue.active().revealId());
    }
    @Test void disablingClearsAllCardsButKeepsDeduplication() {
        var queue = new CaptureRevealQueue();
        queue.offer(reveal("first")); queue.advance(); queue.offer(reveal("second")); queue.clear();
        assertNull(queue.active()); assertFalse(queue.hasWaiting());
        queue.offer(reveal("first")); queue.offer(reveal("second")); assertNull(queue.advance());
    }
    @Test void overflowFallsBackWithoutRememberingAnUndeliveredCapture() {
        var queue = new CaptureRevealQueue();
        for (int i = 0; i < 8; i++) assertTrue(queue.offer(reveal("capture-" + i)));
        assertFalse(queue.offer(reveal("overflow")));
        queue.advance(); assertTrue(queue.offer(reveal("overflow")));
    }
}
