package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SigilDropsTest {
    private final SigilDrops drops = SigilDrops.DEFAULTS;

    @Test void towerChanceRisesWithTheBossFloorAndIsCapped() {
        assertEquals(2, drops.towerPercent(5));
        assertEquals(3, drops.towerPercent(10));
        assertEquals(8, drops.towerPercent(60));
        assertEquals(8, drops.towerPercent(500));
    }

    @Test void exiledChanceRisesPerFloorAndIsCapped() {
        assertEquals(2, drops.exiledPercent(1));
        assertEquals(5, drops.exiledPercent(4));
        assertEquals(10, drops.exiledPercent(30));
    }

    @Test void aRollIsRepeatableAndRoughlyMatchesItsPercent() {
        var player = UUID.randomUUID();
        assertEquals(drops.rollTower("e1", player, 10), drops.rollTower("e1", player, 10));
        int hits = 0;
        for (int i = 0; i < 20_000; i++) if (drops.rollTower("enc-" + i, player, 60)) hits++;
        assertTrue(hits > 1_300 && hits < 1_900, "8 percent of 20,000 is 1,600; got " + hits);
    }

    @Test void aMaximumBelowItsBaseIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SigilDrops(5, 4, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> new SigilDrops(2, 8, 2, 101));
    }
}
