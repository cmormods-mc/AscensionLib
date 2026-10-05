package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.Progression;
import com.cobbleascend.domain.ProfileCodec;
import com.cobbleascend.domain.Rarity;
import com.cobbleascend.domain.Rules;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProfileV1CodecTest {
    private static final String LEGACY = """
            {"schemaVersion":0,"profileId":"e2b02a9c-b02c-4414-8540-f25f60b48eef",
             "pokemonId":"d8927a6a-e60d-44e8-a052-9a4d8c282141","revision":3,"rarity":"RARE",
             "initialRarity":"COMMON","attunement":4,"origin":"wild_capture",
             "affixes":[{"id":"type_focus","type":"fire","value":7},
                        {"id":"physical_force","value":4},
                        {"id":"opening_guard","value":8}]}""";
    private static final UUID POKEMON = UUID.fromString("d8927a6a-e60d-44e8-a052-9a4d8c282141");
    private static final UUID AUTHORITY = UUID.fromString("9d6bb263-d230-42d7-a9d0-ea4710df4bb5");

    private final RankedRules rules = RankedRules.defaults();
    private final RankedProgression progression = new RankedProgression(rules);
    private final ProfileV1Codec codec = new ProfileV1Codec(rules);
    private final SchemaZeroMigration migration = new SchemaZeroMigration(rules);
    private final List<String> types = List.of("fire", "flying");

    private ProfileV1 rich() {
        var random = new Random(8);
        var profile = progression.create(UUID.randomUUID(), AUTHORITY, Rarity.EPIC,
                new Origin("wild_capture", UUID.randomUUID()), 80, types, random);
        profile = progression.upgrade(progression.upgrade(profile, "prefix:0", random).profile(), "suffix:1", random).profile();
        return progression.installUnique(profile, "ashen_heart", UUID.randomUUID()).profile();
    }

    private JsonObject encoded(ProfileV1 profile) { return JsonParser.parseString(codec.encode(profile)).getAsJsonObject(); }

    @Test void roundTripsEveryShapeIncludingUniqueAndAcquisitionId() {
        var random = new Random(3);
        var withUnique = rich();
        assertEquals(withUnique, codec.decode(codec.encode(withUnique), withUnique.pokemonId()));
        assertEquals(codec.encode(withUnique), codec.encode(codec.decode(codec.encode(withUnique), withUnique.pokemonId())));
        for (var rarity : Rarity.values()) {
            for (int level : new int[]{1, 9, 10, 37, 100}) {
                var profile = progression.create(UUID.randomUUID(), AUTHORITY, rarity, Origin.of("hatch"), level, types, random);
                assertEquals(profile, codec.decode(codec.encode(profile), profile.pokemonId()));
            }
        }
        assertNull(codec.decode(codec.encode(progression.create(POKEMON, AUTHORITY, Rarity.COMMON,
                Origin.of("legacy"), 1, types, random)), POKEMON).unique());
    }

    @Test void projectionUsesStableLowercaseIdsAndSpecFieldNames() {
        var json = encoded(rich());
        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals("epic", json.get("rarity").getAsString());
        assertTrue(json.has("authorityId") && json.has("ordinarySlots") && json.has("unique"));
        assertEquals("prefix:0", json.getAsJsonArray("ordinarySlots").get(0).getAsJsonObject().get("slotId").getAsString());
        assertEquals(List.of(10, 20, 30, 40, 50, 60, 70, 80),
                json.getAsJsonArray("awardedMilestones").asList().stream().map(e -> e.getAsInt()).toList());
    }

    @Test void decodeRejectsIdentitySchemaAndShapeViolations() {
        var profile = rich();
        var text = codec.encode(profile);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(text, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("not json", profile.pokemonId()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("[]", profile.pokemonId()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(text + " ".repeat(ProfileV1Codec.MAX_LENGTH), profile.pokemonId()));

        for (int schema : new int[]{0, 2, 99}) {
            var json = encoded(profile);
            json.addProperty("schemaVersion", schema);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(json.toString(), profile.pokemonId()));
        }
        for (String key : encoded(profile).keySet()) {
            if (key.equals("unique")) continue;
            var json = encoded(profile);
            json.remove(key);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(json.toString(), profile.pokemonId()), key);
        }
        var extra = encoded(profile);
        extra.addProperty("surprise", true);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(extra.toString(), profile.pokemonId()));
        var extraSlotKey = encoded(profile);
        extraSlotKey.getAsJsonArray("ordinarySlots").get(0).getAsJsonObject().addProperty("bonus", 1);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(extraSlotKey.toString(), profile.pokemonId()));
    }

    @Test void decodeRejectsBadValuesWithoutRepairingThem() {
        var profile = rich();
        record Mutation(String name, java.util.function.Consumer<JsonObject> apply) {}
        var mutations = List.of(
                new Mutation("fractional rank", j -> slot(j).addProperty("rank", 2.5)),
                new Mutation("string rank", j -> slot(j).addProperty("rank", "2")),
                new Mutation("rank six", j -> slot(j).addProperty("rank", 6)),
                new Mutation("roll outside band", j -> slot(j).addProperty("rolledValue", 99)),
                new Mutation("roll below band", j -> slot(j).addProperty("rolledValue", 0)),
                new Mutation("spent mismatch", j -> j.addProperty("spentUpgradeCredits", 5)),
                new Mutation("negative attunement", j -> j.addProperty("attunement", -1)),
                new Mutation("zero revision", j -> j.addProperty("revision", 0)),
                new Mutation("unknown rarity", j -> j.addProperty("rarity", "mythic")),
                new Mutation("unknown origin", j -> j.getAsJsonObject("origin").addProperty("kind", "found")),
                new Mutation("bad uuid", j -> j.addProperty("profileId", "nope")),
                new Mutation("duplicate milestone", j -> j.getAsJsonArray("awardedMilestones").add(10)),
                new Mutation("non-milestone", j -> j.getAsJsonArray("awardedMilestones").add(15)),
                new Mutation("level below ledger", j -> j.addProperty("highestLevelObserved", 70)),
                new Mutation("unknown affix", j -> slot(j).addProperty("affixId", "mystery")),
                new Mutation("stale catalog", j -> j.addProperty("catalogVersion", 2)),
                new Mutation("unknown unique", j -> j.getAsJsonObject("unique").addProperty("uniqueId", "mystery")),
                new Mutation("unique parameters", j -> slot(j).add("parameters", paramsWithBogusType())));
        for (var mutation : mutations) {
            var json = encoded(profile);
            mutation.apply().accept(json);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(json.toString(), profile.pokemonId()), mutation.name());
        }
        var slots = new JsonArray();
        var json = encoded(profile);
        json.add("ordinarySlots", slots);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(json.toString(), profile.pokemonId()), "slots missing for rarity");
    }

    private static JsonObject slot(JsonObject profile) { return profile.getAsJsonArray("ordinarySlots").get(0).getAsJsonObject(); }

    private static JsonObject paramsWithBogusType() {
        var object = new JsonObject();
        object.addProperty("type", "shadow");
        return object;
    }

    @Test void encodeRefusesAnInvalidProfile() {
        var profile = rich();
        var first = profile.ordinarySlots().getFirst();
        var broken = new ArrayList<>(profile.ordinarySlots());
        broken.set(0, new OrdinarySlot(first.slotId(), first.category(), first.rank(), first.affixId(), first.parameters(),
                100, first.definitionVersion()));
        var invalid = RankedRulesTest.rebuild(profile, 1, profile.awardedMilestones(), profile.highestLevelObserved(),
                profile.spentUpgradeCredits(), broken, profile.unique());
        assertThrows(IllegalArgumentException.class, () -> codec.encode(invalid));
    }

    // --- schema-zero migration fixtures -------------------------------------------------------------

    @Test void schemaZeroFixtureMigratesWithStableSlotsAndExactValues() {
        var migrated = migration.importStored(LEGACY, POKEMON, AUTHORITY, 37);
        assertEquals(UUID.fromString("e2b02a9c-b02c-4414-8540-f25f60b48eef"), migrated.profileId());
        assertEquals(POKEMON, migrated.pokemonId());
        assertEquals(AUTHORITY, migrated.authorityId());
        assertEquals(4, migrated.revision());
        assertEquals(Rarity.RARE, migrated.rarity());
        assertEquals(Rarity.COMMON, migrated.initialRarity());
        assertEquals(Origin.of("wild_capture"), migrated.origin());
        assertEquals(4, migrated.attunement());
        assertEquals(1, migrated.catalogVersion());
        assertNull(migrated.unique());
        assertEquals(Set.of(10, 20, 30), migrated.awardedMilestones());
        assertEquals(37, migrated.highestLevelObserved());
        assertEquals(0, migrated.spentUpgradeCredits());
        assertEquals(3, migrated.pendingCredits());
        assertEquals(List.of(
                new OrdinarySlot("prefix:0", Category.PREFIX, 1, "type_focus", Map.of("type", "fire"), 7, 1),
                new OrdinarySlot("prefix:1", Category.PREFIX, 1, "physical_force", Map.of(), 4, 1),
                new OrdinarySlot("suffix:0", Category.SUFFIX, 1, "opening_guard", Map.of(), 8, 1)),
                migrated.ordinarySlots());
    }

    @Test void migrationIsDeterministicAndRerunningOverItsOutputIsANoOp() {
        var first = migration.importStored(LEGACY, POKEMON, AUTHORITY, 37);
        assertEquals(first, migration.importStored(LEGACY, POKEMON, AUTHORITY, 37));
        var stored = codec.encode(first);
        var again = migration.importStored(stored, POKEMON, UUID.randomUUID(), 100);
        assertEquals(first, again, "A schema-1 string is read back unchanged, whatever level/authority is supplied");
        assertEquals(stored, codec.encode(again));
    }

    @Test void everyPrototypeProfileMigratesPreservingRollsAndOrder() {
        var oldRules = Rules.defaults();
        var oldProgression = new Progression(oldRules);
        var oldCodec = new ProfileCodec(oldRules);
        var random = new Random(77);
        for (var rarity : Rarity.values()) {
            for (int n = 0; n < 200; n++) {
                var legacy = oldProgression.create(UUID.randomUUID(), rarity, "legacy", types, random);
                int level = 1 + random.nextInt(100);
                var migrated = migration.importStored(oldCodec.encode(legacy), legacy.pokemonId(), AUTHORITY, level);
                assertEquals(legacy.profileId(), migrated.profileId());
                assertEquals(legacy.rarity(), migrated.rarity());
                assertEquals(legacy.initialRarity(), migrated.initialRarity());
                assertEquals(legacy.origin(), migrated.origin().kind());
                assertEquals(legacy.affixes().size(), migrated.ordinarySlots().size());
                for (int i = 0; i < legacy.affixes().size(); i++) {
                    var roll = legacy.affixes().get(i);
                    var slot = migrated.ordinarySlots().get(i);
                    assertEquals(roll.id(), slot.affixId());
                    assertEquals(roll.value(), slot.rolledValue());
                    assertEquals(roll.type(), slot.type());
                    assertEquals(1, slot.rank());
                }
                assertEquals(level / 10, migrated.pendingCredits());
                assertEquals(0, migrated.spentUpgradeCredits());
                rules.validate(migrated);
                assertEquals(migrated, codec.decode(codec.encode(migrated), migrated.pokemonId()));
            }
        }
    }

    @Test void migratedProfilesAcceptRankedOperations() {
        var migrated = migration.importStored(LEGACY, POKEMON, AUTHORITY, 37);
        var upgraded = progression.upgrade(migrated, "prefix:0", new Random(1)).profile();
        assertEquals(2, upgraded.slot("prefix:0").orElseThrow().rank());
        assertEquals("fire", upgraded.slot("prefix:0").orElseThrow().type());
        assertEquals(2, upgraded.pendingCredits());
    }

    @Test void invalidOrFutureSourcesAreRejectedNotRepaired() {
        assertThrows(IllegalArgumentException.class, () -> migration.importStored(LEGACY, UUID.randomUUID(), AUTHORITY, 37));
        assertThrows(IllegalArgumentException.class, () -> migration.importStored(LEGACY, POKEMON, AUTHORITY, 0));
        assertThrows(IllegalArgumentException.class, () -> migration.importStored(
                LEGACY.replace("\"value\":8", "\"value\":99"), POKEMON, AUTHORITY, 37));
        assertThrows(IllegalArgumentException.class, () -> migration.importStored(
                LEGACY.replace("opening_guard", "mystery"), POKEMON, AUTHORITY, 37));
        assertThrows(IllegalArgumentException.class, () -> migration.importStored(
                LEGACY.replace("\"schemaVersion\":0", "\"schemaVersion\":7"), POKEMON, AUTHORITY, 37));
        assertThrows(IllegalArgumentException.class, () -> migration.importStored(
                LEGACY.replace("\"schemaVersion\":0,", ""), POKEMON, AUTHORITY, 37));
        assertThrows(IllegalArgumentException.class, () -> migration.importStored("{", POKEMON, AUTHORITY, 37));
        assertThrows(IllegalArgumentException.class, () -> migration.importStored(
                " ".repeat(ProfileV1Codec.MAX_LENGTH + 1), POKEMON, AUTHORITY, 37));
        // A schema-zero profile with one prefix missing for its rarity is invalid at the source.
        assertThrows(IllegalArgumentException.class, () -> migration.importStored(
                LEGACY.replace("{\"id\":\"physical_force\",\"value\":4},", ""), POKEMON, AUTHORITY, 37));
    }
}
