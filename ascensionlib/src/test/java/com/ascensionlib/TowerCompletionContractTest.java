package com.ascensionlib;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TowerCompletionContractTest {
    @Test void withoutARunningWorldNothingIsPaidAndEveryPlayerIsToldDisabled() {
        var player = UUID.randomUUID();
        var result = AscensionRewards.settleTowerCompletion(UUID.randomUUID(), "VICTORY", 10, 0, 3, true, List.of(player));
        assertEquals("DISABLED", result.get(player));
    }

    @Test void aMalformedCallIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> AscensionRewards.settleTowerCompletion(UUID.randomUUID(), "VICTORY", 0, 0, 0, false, List.of(UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class,
                () -> AscensionRewards.settleTowerCompletion(UUID.randomUUID(), "VICTORY", 10, -1, 0, false, List.of(UUID.randomUUID())));
    }

    @Test void thePreviewShowsEveryTierAndMatchesWhatIsPaid() {
        var preview = AscensionRewards.towerCompletionPreview();
        assertEquals(java.util.List.of("BRONZE", "SILVER", "GOLD", "MYTHIC"), List.copyOf(preview.keySet()));
        var bands = com.cobbleascend.domain.v1.TowerCompletionBands.DEFAULTS;
        for (var spec : bands.tiers()) {
            var tier = preview.get(spec.tier().name());
            assertEquals(spec.crownSlots(), tier.get("crownSlots"));
            @SuppressWarnings("unchecked") var pile = (java.util.Map<String, Long>) tier.get("pile");
            bands.pileFor(spec).forEach((material, amount) -> assertEquals(amount, pile.get(material.id())));
            @SuppressWarnings("unchecked") var crown = (java.util.Map<String, Double>) tier.get("crown");
            assertEquals(100.0, crown.values().stream().mapToDouble(Double::doubleValue).sum(), 0.5);
        }
        assertFalse(((java.util.Map<?, ?>) preview.get("BRONZE").get("crown")).containsKey("catalyst"), "Bronze never rolls a Catalyst");
    }

    @Test void theExtrasAgreeWithThePayoutSeedAndAreStable() {
        var encounter = UUID.randomUUID();
        var player = UUID.randomUUID();
        var first = AscensionRewards.towerCompletionExtras(encounter, player, 1, 4, true);
        assertEquals(first, AscensionRewards.towerCompletionExtras(encounter, player, 1, 4, true));
        var bands = com.cobbleascend.domain.v1.TowerCompletionBands.DEFAULTS.payout(encounter.toString(), player, 4, 1, true);
        assertEquals(bands.armorPieces(), first.get("armor"));
        assertEquals(bands.tickets(), first.get("tickets"));
        assertEquals(bands.tier().ordinal(), first.get("tier"));
    }
}
