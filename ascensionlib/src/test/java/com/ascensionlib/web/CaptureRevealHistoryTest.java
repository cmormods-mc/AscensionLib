package com.ascensionlib.web;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CaptureRevealHistoryTest {
    @Test void duplicateCallbacksStayKnownButDifferentCatchesAreIndependent() {
        var history = new CaptureRevealHistory();
        assertFalse(history.contains("first-profile"));
        history.remember("first-profile");
        history.remember("first-profile");
        assertTrue(history.contains("first-profile"));
        assertFalse(history.contains("second-profile"));
        assertFalse(new CaptureRevealHistory().contains("first-profile"), "another player's history is independent");
    }

    @Test void historyIsBoundedWithoutEvictingTheNewestDelivery() {
        var history = new CaptureRevealHistory();
        for (int i = 0; i < 257; i++) history.remember("profile-" + i);
        assertFalse(history.contains("profile-0"));
        assertTrue(history.contains("profile-1"));
        assertTrue(history.contains("profile-256"));
    }
}
