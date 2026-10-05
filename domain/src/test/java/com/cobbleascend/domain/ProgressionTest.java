package com.cobbleascend.domain;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest {
    private final Rules rules = Rules.defaults();
    private final Progression progression = new Progression(rules);
    private final List<String> types = List.of("fire", "flying");

    @Test void everyWeightedTicketHasExactlyThePublishedAllocation() {
        var counts = new EnumMap<Rarity, Integer>(Rarity.class);
        for (int ticket = 0; ticket < rules.totalWeight(); ticket++) counts.merge(rules.rarityAt(ticket), 1, Integer::sum);
        assertEquals(10000, rules.totalWeight());
        assertEquals(Map.of(Rarity.COMMON, 5500, Rarity.UNCOMMON, 2800, Rarity.RARE, 1200,
                Rarity.EPIC, 400, Rarity.LEGENDARY, 90, Rarity.MYTHICAL, 10), counts);
        assertThrows(IllegalArgumentException.class, () -> rules.rarityAt(-1));
        assertThrows(IllegalArgumentException.class, () -> rules.rarityAt(10000));
    }

    @ParameterizedTest @EnumSource(Rarity.class)
    void allRaritiesGenerateFilledValidExclusiveProfiles(Rarity rarity) {
        var random = new Random(12345);
        for (int n = 0; n < 1000; n++) {
            var profile = progression.create(UUID.randomUUID(), rarity, "wild_capture", types, random);
            rules.validate(profile);
            assertEquals(rarity.ordinal() + 1, profile.affixes().size());
            assertEquals(profile.affixes().size(), profile.affixes().stream().map(a -> rules.affix(a.id()).family()).distinct().count());
            for (var roll : profile.affixes()) if (roll.id().equals("type_focus")) assertTrue(types.contains(roll.type()));
        }
    }

    @Test void seededGenerationReproducesRolledOutcomes() {
        var id = UUID.randomUUID();
        var a = progression.create(id, Rarity.MYTHICAL, "wild_capture", types, new Random(4));
        var b = progression.create(id, Rarity.MYTHICAL, "wild_capture", types, new Random(4));
        assertEquals(a.affixes(), b.affixes());
        assertNotEquals(a.profileId(), b.profileId());
    }

    @Test void completePromotionPathPreservesAffixesAndChargesPublishedTotal() {
        var initial = progression.create(UUID.randomUUID(), Rarity.COMMON, "wild_capture", types, new Random(2));
        var profile = withAttunement(initial, 90);
        int dust = 0, facets = 0, cores = 0;
        while (profile.rarity() != Rarity.MYTHICAL) {
            var result = progression.promote(profile, types, new Random(profile.revision()));
            assertEquals(profile.affixes(), result.profile().affixes().subList(0, profile.affixes().size()));
            assertEquals(profile.profileId(), result.profile().profileId());
            assertEquals(profile.pokemonId(), result.profile().pokemonId());
            assertEquals(profile.revision() + 1, result.profile().revision());
            assertEquals(90, result.profile().attunement());
            assertEquals(Rarity.COMMON, result.profile().initialRarity());
            dust += result.cost().dust(); facets += result.cost().facets(); cores += result.cost().cores();
            profile = result.profile();
        }
        assertEquals(new Cost(770, 37, 7), new Cost(dust, facets, cores));
        assertEquals(1, initial.affixes().size());
        var maximum = profile;
        assertThrows(IllegalArgumentException.class, () -> progression.promote(maximum, types, new Random()));
    }

    @Test void promotionChecksDestinationAttunementWithoutMutatingInput() {
        var profile = progression.create(UUID.randomUUID(), Rarity.COMMON, "wild_capture", types, new Random(1));
        assertThrows(IllegalArgumentException.class, () -> progression.promote(withAttunement(profile, 2), types, new Random()));
        assertEquals(Rarity.UNCOMMON, progression.promote(withAttunement(profile, 3), types, new Random()).profile().rarity());
        assertEquals(Rarity.COMMON, profile.rarity());
    }

    @Test void reforgeChangesOnlyChosenSlotAndNeverDuplicatesOtherFamilies() {
        var random = new Random(12);
        var profile = progression.create(UUID.randomUUID(), Rarity.MYTHICAL, "wild_capture", types, random);
        for (int n = 0; n < 600; n++) {
            int index = n % profile.affixes().size();
            var result = progression.reforge(profile, index, types, random);
            for (int other = 0; other < 6; other++) if (other != index)
                assertEquals(profile.affixes().get(other), result.profile().affixes().get(other));
            assertEquals(rules.affix(profile.affixes().get(index).id()).slot(), rules.affix(result.profile().affixes().get(index).id()).slot());
            assertEquals(new Cost(24, 1, 0), result.cost());
            rules.validate(result.profile());
            profile = result.profile();
        }
    }

    @Test void refinementKeepsIdentityAndParameterAndCanRollEveryValue() {
        var profile = progression.create(UUID.randomUUID(), Rarity.COMMON, "wild_capture", types, new Random(9));
        var old = profile.affixes().getFirst();
        var definition = rules.affix(old.id());
        var outcomes = new HashSet<Integer>();
        var random = new Random(100);
        for (int n = 0; n < 500; n++) {
            var result = progression.refine(profile, 0, random);
            var roll = result.profile().affixes().getFirst();
            assertEquals(old.id(), roll.id());
            assertEquals(old.type(), roll.type());
            assertEquals(new Cost(12, 0, 0), result.cost());
            outcomes.add(roll.value());
        }
        assertEquals(definition.max() - definition.min() + 1, outcomes.size());
        assertTrue(outcomes.contains(old.value()), "Same-value refinement is allowed");
    }

    @Test void profileCodecRoundTripsAndRejectsIdentityAndSchemaMismatch() {
        var codec = new ProfileCodec(rules);
        var profile = progression.create(UUID.randomUUID(), Rarity.EPIC, "wild_capture", types, new Random(4));
        var encoded = codec.encode(profile);
        assertEquals(profile, codec.decode(encoded, profile.pokemonId()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded, UUID.randomUUID()));
        var future = JsonParser.parseString(encoded).getAsJsonObject();
        future.addProperty("schemaVersion", 99);
        assertThrows(RuntimeException.class, () -> codec.decode(future.toString(), profile.pokemonId()));
        future.addProperty("schemaVersion", 0);
        future.getAsJsonArray("affixes").get(0).getAsJsonObject().addProperty("value", 99);
        assertThrows(RuntimeException.class, () -> codec.decode(future.toString(), profile.pokemonId()));
    }

    @Test void profileCannotBeMutatedThroughCallerList() {
        var source = progression.create(UUID.randomUUID(), Rarity.COMMON, "admin", types, new Random());
        var mutable = new ArrayList<>(source.affixes());
        var copy = new Profile(0, source.profileId(), source.pokemonId(), 1, source.rarity(), source.initialRarity(), 0, "admin", mutable);
        mutable.clear();
        assertEquals(1, copy.affixes().size());
        assertThrows(UnsupportedOperationException.class, () -> copy.affixes().clear());
    }

    @Test void invalidFamiliesParametersAndMissingSlotsAreRejected() {
        var base = progression.create(UUID.randomUUID(), Rarity.UNCOMMON, "admin", types, new Random());
        assertThrows(IllegalArgumentException.class, () -> rules.validate(base.withProgress(Rarity.UNCOMMON,
                List.of(new AffixRoll("physical_force", null, 4), new AffixRoll("special_force", null, 4)))));
        assertThrows(IllegalArgumentException.class, () -> rules.validate(base.withProgress(Rarity.COMMON, List.of())));
        assertThrows(RuntimeException.class, () -> rules.validate(base.withProgress(Rarity.COMMON,
                List.of(new AffixRoll("type_focus", "unknown", 4)))));
    }

    @Test void malformedRulesFailBeforeUse() {
        assertThrows(IllegalArgumentException.class, () -> new AffixDefinition("bad", "Bad", "prefix", "family", "outgoingDamage", 8, 4, 1, null, "Any"));
        assertThrows(IllegalArgumentException.class, () -> new Cost(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Rules.parse(
                JsonParser.parseString("{\"schemaVersion\":99}").getAsJsonObject(),
                JsonParser.parseString("{\"schemaVersion\":1}").getAsJsonObject()));
    }

    private Profile withAttunement(Profile p, int value) {
        return new Profile(p.schemaVersion(), p.profileId(), p.pokemonId(), p.revision(), p.rarity(), p.initialRarity(), value, p.origin(), p.affixes());
    }
}
