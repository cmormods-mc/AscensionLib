package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.Rarity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResonanceTest {
    private static OrdinarySlot slot(String id, int index, int value) {
        return new OrdinarySlot("prefix:" + index, Category.PREFIX, 1, id, Map.of(), value, 1);
    }

    private static CombatSnapshot snapshot(OrdinarySlot... slots) {
        return new CombatSnapshot(CombatSnapshot.Source.PLAYER, "resonance", Rarity.MYTHICAL, 1, List.of(slots), null);
    }

    @Test void everyThemedAffixExistsInTheCatalog() {
        var rules = RankedRules.defaults();
        for (var id : Resonance.themes().keySet()) assertNotNull(rules.affix(id), id + " is themed but not in the catalog");
    }

    @Test void multipliersFollowTheThresholds() {
        assertEquals(100, Resonance.multiplierPercent(0));
        assertEquals(100, Resonance.multiplierPercent(1));
        assertEquals(115, Resonance.multiplierPercent(2));
        assertEquals(130, Resonance.multiplierPercent(3));
        assertEquals(130, Resonance.multiplierPercent(6));
    }

    @Test void aLonePieceIsUnchanged() {
        var effects = BattleFx.effectsOf(snapshot(slot("executioner", 0, 20), slot("restorative", 1, 10)));
        assertEquals(20, effects.get(0).percent());
        assertEquals(10, effects.get(1).percent());
    }

    @Test void twoPiecesOfOneThemeAreBoostedRoundedHalfUp() {
        var effects = BattleFx.effectsOf(snapshot(slot("executioner", 0, 20), slot("last_stand", 1, 10)));
        assertEquals(23, effects.get(0).percent(), "20 x 1.15 = 23");
        assertEquals(12, effects.get(1).percent(), "10 x 1.15 = 11.5 rounds up to 12");
    }

    @Test void threePiecesUseTheLargerMultiplier() {
        var effects = BattleFx.effectsOf(snapshot(slot("executioner", 0, 20), slot("last_stand", 1, 10),
                slot("super_effective_force", 2, 30)));
        assertEquals(26, effects.get(0).percent());
        assertEquals(13, effects.get(1).percent());
        assertEquals(39, effects.get(2).percent());
    }

    @Test void neutralAffixesAndOtherThemesAreNeverBoosted() {
        var effects = BattleFx.effectsOf(snapshot(slot("executioner", 0, 20), slot("last_stand", 1, 10),
                slot("physical_force", 2, 15), slot("restorative", 3, 12)));
        assertEquals(23, effects.get(0).percent());
        assertEquals(15, effects.get(2).percent(), "physical_force has no theme");
        assertEquals(12, effects.get(3).percent(), "restorative is alone in its theme");
    }

    @Test void aUniqueIsNotAffectedByResonance() {
        var plain = snapshot(slot("executioner", 0, 20), slot("last_stand", 1, 10));
        var withUnique = new CombatSnapshot(plain.source(), plain.subjectId(), plain.rarity(), plain.catalogVersion(), plain.slots(), "ashen_heart");
        var last = BattleFx.effectsOf(withUnique).get(2);
        assertEquals(1, last.percent());
    }

    @Test void differentThemesDoNotPoolTheirPieces() {
        var pieces = Resonance.pieces(List.of(slot("executioner", 0, 20), slot("restorative", 1, 10)));
        assertEquals(1, pieces.get(Resonance.Theme.PREDATOR));
        assertEquals(1, pieces.get(Resonance.Theme.VITALITY));
    }
}
