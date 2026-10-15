package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DungeonRewardBandsTest {
    private final DungeonRewardBands bands = DungeonRewardBands.DEFAULTS;
    private final UUID player = UUID.randomUUID();

    @Test void aRepeatedCallbackComputesIdenticalAmounts() {
        assertEquals(bands.payout("run-1", player, 3, 4, true), bands.payout("run-1", player, 3, 4, true));
    }

    @Test void dustGrowsWithTierArenaWinsAndABossAndNothingElseIsGuaranteed() {
        long base = bands.payout("r", player, 1, 0, false).get(MaterialId.RESONANCE_DUST);
        assertEquals(10, base);
        assertTrue(bands.payout("r", player, 5, 0, false).get(MaterialId.RESONANCE_DUST) > base);
        assertTrue(bands.payout("r", player, 1, 3, false).get(MaterialId.RESONANCE_DUST) > base);
        assertTrue(bands.payout("r", player, 1, 0, true).get(MaterialId.RESONANCE_DUST) > base);
        assertEquals(java.util.Set.of(MaterialId.RESONANCE_DUST), bands.payout("r", player, 1, 0, false).keySet());
    }

    @Test void aBossAlwaysPaysAFacetAndAFragmentAndNeverACore() {
        for (int n = 0; n < 200; n++) {
            var payout = bands.payout("run-" + n, player, 1 + n % 10, n % 5, true);
            assertTrue(payout.get(MaterialId.FACET) >= 1);
            assertTrue(payout.get(MaterialId.UNIQUE_FRAGMENT) >= 1 && payout.get(MaterialId.UNIQUE_FRAGMENT) <= 2);
            assertFalse(payout.containsKey(MaterialId.ASCENSION_CORE), "Cores stay a Tower reward");
        }
    }

    @Test void arenaFacetsAverageTheConfiguredChance() {
        long facets = 0;
        int runs = 4000;
        for (int n = 0; n < runs; n++) facets += bands.payout("run-" + n, player, 2, 10, false).getOrDefault(MaterialId.FACET, 0L);
        double perArena = facets / (runs * 10.0);
        assertEquals(0.35, perArena, 0.03);
    }

    @Test void badInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> bands.payout("r", player, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> bands.payout("r", player, 11, 0, false));
        assertThrows(IllegalArgumentException.class, () -> bands.payout("r", player, 1, 17, false));
        assertThrows(IllegalArgumentException.class, () -> bands.payout("r", player, 1, -1, false));
    }
}
