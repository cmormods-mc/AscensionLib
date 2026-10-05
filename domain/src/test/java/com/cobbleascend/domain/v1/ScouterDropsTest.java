package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ScouterDropsTest {
    @Test void defaultsAreFivePercentAndFifteenOnAKeenEyeFloor() {
        assertEquals(5, ScouterDrops.DEFAULTS.percent(false));
        assertEquals(15, ScouterDrops.DEFAULTS.percent(true));
    }

    @Test void observedRatesMatchTheConfiguredChances() {
        var random = new Random(11);
        int plain = 0, keen = 0, n = 200_000;
        for (int i = 0; i < n; i++) {
            if (ScouterDrops.DEFAULTS.roll(false, random)) plain++;
            if (ScouterDrops.DEFAULTS.roll(true, random)) keen++;
        }
        assertEquals(0.05, plain / (double) n, 0.004);
        assertEquals(0.15, keen / (double) n, 0.006);
    }

    @Test void seededRollIsStableAndMatchesTheChance() {
        var player = new java.util.UUID(5, 5);
        for (int i = 0; i < 200; i++)
            assertEquals(ScouterDrops.DEFAULTS.rollFor(false, "e" + i, player), ScouterDrops.DEFAULTS.rollFor(false, "e" + i, player));
        int hits = 0, n = 100_000;
        for (int i = 0; i < n; i++) if (ScouterDrops.DEFAULTS.rollFor(true, "e" + i, new java.util.UUID(i, 1))) hits++;
        assertEquals(0.15, hits / (double) n, 0.006);
    }

    @Test void invalidChancesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ScouterDrops(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new ScouterDrops(10, 5));
        assertThrows(IllegalArgumentException.class, () -> new ScouterDrops(5, 101));
        assertFalse(new ScouterDrops(0, 0).roll(true, new Random(1)));
        assertTrue(new ScouterDrops(100, 100).roll(false, new Random(1)));
    }
}
