package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.TowerCompletionBands.Tier;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TowerCompletionBandsTest {
    private static final TowerCompletionBands BANDS = TowerCompletionBands.DEFAULTS;

    @Test void scoreIsCardsPlusTwoPerCyclePlusTwoWhenFlawless() {
        assertEquals(0, TowerCompletionBands.score(0, 0, false));
        assertEquals(2, TowerCompletionBands.score(0, 0, true));
        assertEquals(8, TowerCompletionBands.score(2, 2, true));
        assertThrows(IllegalArgumentException.class, () -> TowerCompletionBands.score(-1, 0, false));
    }

    @Test void thresholdsMapScoresToTiers() {
        assertEquals(Tier.BRONZE, BANDS.tierFor(0).tier());
        assertEquals(Tier.BRONZE, BANDS.tierFor(1).tier());
        assertEquals(Tier.SILVER, BANDS.tierFor(2).tier());
        assertEquals(Tier.SILVER, BANDS.tierFor(4).tier());
        assertEquals(Tier.GOLD, BANDS.tierFor(5).tier());
        assertEquals(Tier.GOLD, BANDS.tierFor(7).tier());
        assertEquals(Tier.MYTHIC, BANDS.tierFor(8).tier());
        assertEquals(Tier.MYTHIC, BANDS.tierFor(99).tier());
    }

    @Test void aRetryComputesTheIdenticalChest() {
        var player = UUID.randomUUID();
        for (int i = 0; i < 100; i++)
            assertEquals(BANDS.payout("e" + i, player, 3, 1, true), BANDS.payout("e" + i, player, 3, 1, true));
    }

    @Test void speedNeverCountsOnlyDifficultyAndCleanness() {
        var player = UUID.randomUUID();
        assertEquals(Tier.SILVER, BANDS.payout("e", player, 0, 0, true).tier());
        assertEquals(Tier.BRONZE, BANDS.payout("e", player, 0, 0, false).tier());
    }

    @Test void theSilverPileIsTheDocumentedOne() {
        var silver = BANDS.payout("e", UUID.randomUUID(), 2, 0, false);
        assertEquals(Tier.SILVER, silver.tier());
        assertEquals(800L, silver.materials().get(MaterialId.RESONANCE_DUST));
        assertEquals(6L, silver.materials().get(MaterialId.FACET));
        assertEquals(4L, silver.materials().get(MaterialId.SCOUTER));
        assertEquals(3L, silver.materials().get(MaterialId.TOWER_MARK));
        assertTrue(silver.materials().get(MaterialId.UNIQUE_FRAGMENT) >= 3);
    }

    @Test void tiersScaleThePileAndTheCrownSlotsAndMarks() {
        var player = UUID.randomUUID();
        var bronze = BANDS.payout("e", player, 0, 0, false);
        var gold = BANDS.payout("e", player, 5, 0, false);
        var mythic = BANDS.payout("e", player, 8, 0, false);
        assertEquals(560L, bronze.materials().get(MaterialId.RESONANCE_DUST));
        assertEquals(1200L, gold.materials().get(MaterialId.RESONANCE_DUST));
        assertEquals(1800L, mythic.materials().get(MaterialId.RESONANCE_DUST));
        assertEquals(1, bronze.crowns().size());
        assertEquals(2, gold.crowns().size());
        assertEquals(3, mythic.crowns().size());
        assertEquals(3L, bronze.materials().get(MaterialId.TOWER_MARK));
        assertEquals(4L, gold.materials().get(MaterialId.TOWER_MARK));
        assertEquals(5L, mythic.materials().get(MaterialId.TOWER_MARK));
    }

    @Test void everyCompletionHasACrownAndCrownMaterialsAreCredited() {
        for (int i = 0; i < 5000; i++) {
            var result = BANDS.payout("e" + i, new UUID(i, 1), i % 12, i % 3, i % 2 == 0);
            assertFalse(result.crowns().isEmpty(), "no empty hours");
            long cores = result.crowns().stream().filter(c -> c == TowerCompletionBands.Crown.CORE).count();
            assertEquals(cores, result.materials().getOrDefault(MaterialId.ASCENSION_CORE, 0L));
            long armor = result.crowns().stream().filter(c -> c == TowerCompletionBands.Crown.ARMOR).count();
            assertEquals(armor, result.armorPieces());
        }
    }

    @Test void bronzeNeverRollsACatalyst() {
        for (int i = 0; i < 5000; i++)
            assertFalse(BANDS.payout("e" + i, new UUID(i, 2), 0, 0, false).materials().containsKey(MaterialId.UNIQUE_CATALYST));
    }

    @Test void meansMatchTheApprovedModel() {
        // Tier mix 15 / 45 / 30 / 10 via cards chosen to land in each tier; the F5 mini chest adds 200 dust, 1 facet, 1 fragment, 1 mark and 5 percent of a core.
        int[] cards = {0, 2, 5, 8};
        double[] share = {0.15, 0.45, 0.30, 0.10};
        int n = 40_000;
        double dust = 0, cores = 0, fragments = 0, sigils = 0;
        for (int t = 0; t < 4; t++) {
            double d = 0, c = 0, f = 0, s = 0;
            for (int i = 0; i < n; i++) {
                var result = BANDS.payout("e" + i, new UUID(i, 9), cards[t], 0, false);
                d += result.materials().get(MaterialId.RESONANCE_DUST);
                c += result.materials().getOrDefault(MaterialId.ASCENSION_CORE, 0L);
                f += result.materials().getOrDefault(MaterialId.UNIQUE_FRAGMENT, 0L);
                s += result.materials().getOrDefault(MaterialId.ASCENSION_SIGIL, 0L);
            }
            dust += share[t] * d / n; cores += share[t] * c / n; fragments += share[t] * f / n; sigils += share[t] * s / n;
        }
        assertEquals(1184 - 200, dust, 15);
        assertEquals(0.59 - 0.05, cores, 0.03);
        assertEquals(9.0 - 1, fragments, 0.4);
        assertEquals(0.19, sigils, 0.03);
    }

    @Test void malformedBandsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new TowerCompletionBands.TierSpec(Tier.GOLD, 5, 100, 1, new int[] {1, 2}));
        assertThrows(IllegalArgumentException.class, () -> new TowerCompletionBands(1, 1, 1, 1, 1, 0, 0, 0, BANDS.tiers()));
    }
}
