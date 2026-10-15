package com.ascensionlib.reward;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.TowerCompletionBands;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompletionRevealTest {
    private static CompletionReveal.View view(int cards) {
        var completion = TowerCompletionBands.DEFAULTS.payout("e", new UUID(1, 2), cards, 0, false);
        return CompletionReveal.view(completion, 0, cards, false, 3, false);
    }

    @Test void theViewCarriesTheTierMeterWithEveryThreshold() {
        var view = view(8);
        assertEquals("mythic", view.tier());
        assertEquals(java.util.List.of(0, 2, 5, 8), view.tiers().stream().map(CompletionReveal.TierMark::from).toList());
    }

    @Test void theSharedLineNamesThePlayerTierScoreDustMarksAndCrown() {
        var line = CompletionReveal.shareLine("Alice", view(8));
        assertTrue(line.startsWith("[Tower] Alice: MYTHIC completion (score 8)"), line);
        assertTrue(line.contains("Resonance Dust") && line.contains("Tower Mark") && line.contains("crown:"), line);
    }

    @Test void aPlainBronzeCompletionIsNotAnnouncedUnlessItRolledACoreOrACatalyst() {
        for (int i = 0; i < 200; i++) {
            var completion = TowerCompletionBands.DEFAULTS.payout("e" + i, new UUID(i, 3), 0, 0, false);
            var view = CompletionReveal.view(completion, 0, 0, false, 3, false);
            boolean worthIt = completion.crowns().contains(TowerCompletionBands.Crown.CORE) || completion.crowns().contains(TowerCompletionBands.Crown.CATALYST);
            assertEquals(worthIt, !CompletionReveal.announcement("Bob", view).isEmpty());
        }
    }
}
