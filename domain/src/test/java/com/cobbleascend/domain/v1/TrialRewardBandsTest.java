package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TrialRewardBandsTest {
    @Test void ranksFollowTheTowerScoutingTiers() {
        assertEquals(1, TrialRewardBands.rankForFloorLimit(1));
        assertEquals(1, TrialRewardBands.rankForFloorLimit(4));
        assertEquals(2, TrialRewardBands.rankForFloorLimit(5));
        assertEquals(2, TrialRewardBands.rankForFloorLimit(9));
        assertEquals(3, TrialRewardBands.rankForFloorLimit(10));
        assertEquals(3, TrialRewardBands.rankForFloorLimit(40));
        assertThrows(IllegalArgumentException.class, () -> TrialRewardBands.rankForFloorLimit(0));
    }

    @Test void payoutsFollowTheApprovedTableWithoutCores() {
        assertEquals(Map.of(MaterialId.RESONANCE_DUST, 6L), TrialRewardBands.payout(1));
        assertEquals(Map.of(MaterialId.RESONANCE_DUST, 10L, MaterialId.FACET, 1L), TrialRewardBands.payout(2));
        var top = TrialRewardBands.payout(3);
        assertEquals(Map.of(MaterialId.RESONANCE_DUST, 16L, MaterialId.FACET, 1L), top);
        assertFalse(top.containsKey(MaterialId.ASCENSION_CORE));
        assertThrows(IllegalArgumentException.class, () -> TrialRewardBands.payout(0));
        assertThrows(IllegalArgumentException.class, () -> TrialRewardBands.payout(4));
    }
}
