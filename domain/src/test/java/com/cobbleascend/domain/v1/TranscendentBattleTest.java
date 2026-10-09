package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.Rarity;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A Transcendent reaching the simulator: from the recipe book, through the frozen snapshot, to the battle payload. */
class TranscendentBattleTest {
    private static final RankedRules RULES = RankedRules.defaults();
    private static final Transcendence BOOK = Transcendence.defaults();

    private static OrdinarySlot slot(String id, int index, int value) {
        return new OrdinarySlot("prefix:" + index, Category.PREFIX, 1, id, Map.of(), value, 1);
    }

    private static CombatSnapshot snapshot(CombatSnapshot.Fused fused, OrdinarySlot... slots) {
        return new CombatSnapshot(CombatSnapshot.Source.PLAYER, "fused", Rarity.MYTHICAL, 1, List.of(slots), null, fused);
    }

    @Test void aFusionFromTheRecipeBookReachesThePayloadAsOneEffect() {
        var result = BOOK.resolve("charizard", "ashen_heart", "gyarados", "last_breath");
        var snapshot = snapshot(result.toFused(), slot("executioner", 0, 20));
        var effects = BattleFx.effectsOf(snapshot);
        assertEquals(3, effects.size(), "one ordinary slot, the donor's rider, and one Transcendent (not two Uniques)");
        assertEquals("restorative", effects.get(1).affixId(), "a Tide donor adds Restorative");
        var effect = effects.get(2);
        assertEquals("transcendent", effect.affixId());
        assertEquals("Fire", effect.type(), "the host type is capitalised for Showdown");

        var json = JsonParser.parseString(BattleFx.payload(Map.of("uuid-1", effects), RULES.base())).getAsJsonObject()
                .getAsJsonObject("mons").getAsJsonArray("uuid-1").get(2).getAsJsonObject();
        assertEquals("transcendent", json.get("i").getAsString());
        assertEquals("phoenix_cinder", json.get("sg").getAsString(), "the signature travels with the effect");
        assertEquals("Scorch", result.twist().name());
        assertEquals("brn", json.getAsJsonArray("tw").get(0).getAsJsonObject().get("status").getAsString());
        assertEquals("ashen_heart", json.getAsJsonArray("u").get(0).getAsString());
        assertEquals("last_breath", json.getAsJsonArray("u").get(1).getAsString());
        assertEquals(result.benefitPercent(), json.get("b").getAsInt());
        assertEquals(result.drawbackPercent(), json.get("d").getAsInt());
        assertEquals("Fire", json.get("t").getAsString());
        assertEquals("Water", json.get("r").getAsString());
    }

    @Test void aMissingTypeIsLeftOutOfThePayload() {
        var fused = new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 80, 60, null, null);
        var effects = BattleFx.effectsOf(snapshot(fused));
        var json = JsonParser.parseString(BattleFx.payload(Map.of("u1", effects), RULES.base())).getAsJsonObject()
                .getAsJsonObject("mons").getAsJsonArray("u1").get(0).getAsJsonObject();
        assertFalse(json.has("t"));
        assertFalse(json.has("r"));
    }

    @Test void resonanceStillAppliesToTheOrdinarySlotsOfAFusedPokemon() {
        var fused = new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 80, 60, "fire", "water");
        var effects = BattleFx.effectsOf(snapshot(fused, slot("executioner", 0, 20), slot("last_stand", 1, 10)));
        assertEquals(23, effects.get(0).percent(), "20 x 1.15");
        assertEquals(12, effects.get(1).percent());
        assertEquals("transcendent", effects.get(2).affixId());
    }

    @Test void aSnapshotHoldsAUniqueOrATranscendentNeverBoth() {
        var fused = new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 80, 60, "fire", "water");
        assertThrows(IllegalArgumentException.class,
                () -> new CombatSnapshot(CombatSnapshot.Source.PLAYER, "x", Rarity.COMMON, 1, List.of(), "ashen_heart", fused));
    }

    @Test void aMalformedFusionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CombatSnapshot.Fused(List.of("rupture", "rupture"), 80, 60, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CombatSnapshot.Fused(List.of("rupture"), 80, 60, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CombatSnapshot.Fused(List.of("rupture", "Last Breath"), 80, 60, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 0, 60, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 80, 101, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 80, 60, "Fire!", null));
    }

    @Test void theCatalogRejectsAFusionOfAnUnknownUnique() {
        var slots = List.of(slot("executioner", 0, 8));   // a Common holds one prefix; the roll sits in executioner's rank I band
        var good = new CombatSnapshot(CombatSnapshot.Source.PLAYER, "ok", Rarity.COMMON, 1, slots, null,
                new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 80, 60, "fire", "water"));
        RULES.validate(good);
        var bad = new CombatSnapshot(CombatSnapshot.Source.PLAYER, "bad", Rarity.COMMON, 1, slots, null,
                new CombatSnapshot.Fused(List.of("rupture", "not_a_unique"), 80, 60, "fire", "water"));
        assertThrows(IllegalArgumentException.class, () -> RULES.validate(bad));
    }

    @Test void theContentHashSeparatesAFusionFromAPlainSnapshotAndFromAnotherFusion() {
        var plain = snapshot(null);
        var one = snapshot(new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 80, 60, "fire", "water"));
        var other = snapshot(new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 75, 60, "fire", "water"));
        assertNotEquals(plain.contentHash(), one.contentHash());
        assertNotEquals(one.contentHash(), other.contentHash());
        assertEquals(one.contentHash(), snapshot(new CombatSnapshot.Fused(List.of("rupture", "last_breath"), 80, 60, "fire", "water")).contentHash());
    }

    @Test void aFullMythicalWithATranscendentFitsTheFormatField() {
        var fused = BOOK.resolve("kyogre", "ashen_heart", "groudon", "last_breath").toFused();
        var slots = new OrdinarySlot[] {slot("executioner", 0, 20), slot("last_stand", 1, 10), slot("type_focus", 2, 8),
                new OrdinarySlot("suffix:0", Category.SUFFIX, 1, "iron_resolve", Map.of(), 9, 1),
                new OrdinarySlot("suffix:1", Category.SUFFIX, 1, "restorative", Map.of(), 10, 1),
                new OrdinarySlot("suffix:2", Category.SUFFIX, 1, "healthy_guard", Map.of(), 5, 1)};
        var payload = BattleFx.payload(Map.of("11111111-1111-1111-1111-111111111111", BattleFx.effectsOf(snapshot(fused, slots))), RULES.base());
        assertTrue(payload.length() < BattleFx.MAX_PAYLOAD_CHARS, "payload length " + payload.length());
    }
}
