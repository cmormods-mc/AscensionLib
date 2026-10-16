package com.ascensionlib;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AscensionScreensTest {
    @Test
    @DisplayName("an opened reveal counts as showing through its load, stays showing while it beats, and stops when it goes quiet or is released")
    void showingFollowsTheHeartbeat() {
        UUID player = UUID.randomUUID();
        assertFalse(AscensionScreens.revealShowing(player, 0));
        AscensionScreens.revealOpened(player, 1_000);
        assertTrue(AscensionScreens.revealShowing(player, 1_000), "the same tick the page is opened");
        assertTrue(AscensionScreens.revealShowing(player, 8_900), "still loading");
        assertFalse(AscensionScreens.revealShowing(player, 9_100), "never said hello: it did not open");

        AscensionScreens.revealOpened(player, 20_000);
        AscensionScreens.revealBeat(player, 21_500);
        AscensionScreens.revealBeat(player, 23_000);
        assertTrue(AscensionScreens.revealShowing(player, 27_900));
        assertFalse(AscensionScreens.revealShowing(player, 28_100), "closed with Esc: the beats stopped");

        AscensionScreens.revealOpened(player, 40_000);
        AscensionScreens.revealClosed(player);
        assertFalse(AscensionScreens.revealShowing(player, 40_001), "closed with its own button");
    }

    @Test
    @DisplayName("a late beat cannot shorten a longer load grace")
    void beatsOnlyExtend() {
        UUID player = UUID.randomUUID();
        AscensionScreens.revealOpened(player, 0);
        AscensionScreens.revealBeat(player, 100);
        assertTrue(AscensionScreens.revealShowing(player, 7_900));
    }
}
