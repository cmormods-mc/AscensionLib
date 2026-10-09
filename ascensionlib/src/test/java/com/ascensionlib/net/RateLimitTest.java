package com.ascensionlib.net;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RateLimitTest {
    private static final long SECOND = 1_000_000_000L;
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID();

    @AfterEach void reset() { RateLimit.clear(); }

    @Test void aBurstIsAllowedThenRefused() {
        for (int i = 0; i < 4; i++) assertTrue(RateLimit.allow(a, "x", 4, 2, 0), "token " + i);
        assertFalse(RateLimit.allow(a, "x", 4, 2, 0), "the fifth in the same instant is refused");
    }

    @Test void tokensComeBackAtTheRate() {
        for (int i = 0; i < 4; i++) RateLimit.allow(a, "x", 4, 2, 0);
        assertFalse(RateLimit.allow(a, "x", 4, 2, SECOND / 4), "a quarter second at 2 a second is half a token");
        assertTrue(RateLimit.allow(a, "x", 4, 2, SECOND / 2), "half a second gives one");
        assertFalse(RateLimit.allow(a, "x", 4, 2, SECOND / 2), "and only one");
        assertTrue(RateLimit.allow(a, "x", 4, 2, 5 * SECOND), "after a long wait the bucket is full again");
        for (int i = 0; i < 3; i++) assertTrue(RateLimit.allow(a, "x", 4, 2, 5 * SECOND));
        assertFalse(RateLimit.allow(a, "x", 4, 2, 5 * SECOND), "but never more than the burst");
    }

    @Test void playersAndChannelsAreIndependent() {
        for (int i = 0; i < 4; i++) RateLimit.allow(a, "x", 4, 2, 0);
        assertFalse(RateLimit.allow(a, "x", 4, 2, 0));
        assertTrue(RateLimit.allow(b, "x", 4, 2, 0), "another player is not affected");
        assertTrue(RateLimit.allow(a, "y", 4, 2, 0), "another channel is not affected");
    }

    @Test void leavingForgetsThePlayer() {
        RateLimit.allow(a, "x", 4, 2, 0);
        RateLimit.allow(b, "x", 4, 2, 0);
        assertEquals(2, RateLimit.tracked());
        RateLimit.forget(a);
        assertEquals(1, RateLimit.tracked());
        for (int i = 0; i < 4; i++) assertTrue(RateLimit.allow(a, "x", 4, 2, 0), "a returning player starts with a full bucket");
    }

    @Test void nonsenseSettingsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> RateLimit.allow(a, "x", 0, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> RateLimit.allow(a, "x", 4, 0, 0));
    }
}
