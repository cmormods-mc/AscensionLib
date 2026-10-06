package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TowerRewardBandsTest {
    private static final TowerRewardBands BANDS = TowerRewardBands.DEFAULTS;

    @Test void firstSegmentPaysAboutTwelveDustAndSecondAboutTwentyThree() {
        assertEquals(12L, dust(1, 5));
        assertEquals(23L, dust(6, 10));
    }

    @Test void aFullRunToFloorTenPaysAboutThirtyFiveDust() {
        assertEquals(35L, dust(1, 5) + dust(6, 10));
    }

    @Test void bossDustGrowsPastTheMilestoneFloor() {
        assertEquals(12.0, BANDS.bossDust(10), 1e-9);
        assertEquals(12.0 * 1.08 * 1.08 * 1.08 * 1.08 * 1.08, BANDS.bossDust(15), 1e-9);
        assertEquals(5.0, BANDS.bossDust(5), 1e-9);
    }

    @Test void repeatingTheCallPaysIdenticalAmounts() {
        var player = UUID.randomUUID();
        for (int i = 0; i < 200; i++) {
            var id = "tower-run-" + i;
            assertEquals(BANDS.payout(id, player, 6, 10), BANDS.payout(id, player, 6, 10));
        }
    }

    @Test void observedRatesMatchTheBands() {
        int n = 100_000, facetsFirst = 0, facetsMilestone = 0, cores = 0, fragments = 0;
        for (int i = 0; i < n; i++) {
            var id = "e" + i;
            var player = new UUID(i, 7);
            if (BANDS.payout(id, player, 1, 5).containsKey(MaterialId.FACET)) facetsFirst++;
            var milestone = BANDS.payout(id, player, 6, 10);
            if (milestone.containsKey(MaterialId.FACET)) facetsMilestone++;
            if (milestone.containsKey(MaterialId.ASCENSION_CORE)) cores++;
            fragments += milestone.get(MaterialId.UNIQUE_FRAGMENT);
        }
        assertEquals(0.50, facetsFirst / (double) n, 0.01);
        assertEquals(1.00, facetsMilestone / (double) n, 0.0);
        assertEquals(0.075, cores / (double) n, 0.005);
        assertEquals(1.43, fragments / (double) n, 0.01);
        assertEquals(35.0, 100 / (2 * 1.43), 0.1, "a Catalyst about every 35 runs to floor 10");
    }

    @Test void playersInOneEncounterGetTheSameDustButIndependentRolls() {
        var a = BANDS.payout("same", new UUID(1, 1), 6, 10);
        var b = BANDS.payout("same", new UUID(2, 2), 6, 10);
        assertEquals(a.get(MaterialId.RESONANCE_DUST), b.get(MaterialId.RESONANCE_DUST));
    }

    @Test void neverPaysScoutersOrCatalysts() {
        for (int i = 0; i < 2000; i++) {
            var payout = BANDS.payout("e" + i, new UUID(i, 3), 6, 10);
            assertFalse(payout.containsKey(MaterialId.SCOUTER));
            assertFalse(payout.containsKey(MaterialId.UNIQUE_CATALYST));
        }
    }

    @Test void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BANDS.payout("e", UUID.randomUUID(), 0, 5));
        assertThrows(IllegalArgumentException.class, () -> BANDS.payout("e", UUID.randomUUID(), 6, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new TowerRewardBands(1.6, 1.08, 5, 10, 12, 101, 100, 75, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new TowerRewardBands(1.6, 1.08, 5, 10, 12, 50, 100, 1001, 5));
    }

    private static long dust(int from, int boss) {
        return BANDS.payout("run", new UUID(9, 9), from, boss).get(MaterialId.RESONANCE_DUST);
    }
}
