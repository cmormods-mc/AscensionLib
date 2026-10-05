package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.Rarity;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;

class EnemyGeneratorTest {
    private final RankedRules rules = RankedRules.defaults();
    private final EnemyGenerator generator = new EnemyGenerator(rules);
    private final EnemyTiers tiers = EnemyTiers.defaults();
    private final List<String> types = List.of("water", "dark");

    private static EnemyTiers parse(String json) { return EnemyTiers.parse(JsonParser.parseString(json).getAsJsonObject()); }

    @Test void defaultTiersLoadAndAreValid() {
        assertEquals(4, tiers.all().size());
        assertEquals(8, tiers.tier("trial_rank_3").rankCredits());
        assertEquals(List.of(Rarity.LEGENDARY, Rarity.MYTHICAL),
                tiers.tier("boss").rarities().stream().map(EnemyTier.RarityWeight::rarity).toList());
        assertThrows(IllegalArgumentException.class, () -> tiers.tier("missing"));
    }

    @Test void malformedTierConfigurationIsRejected() {
        String base = "{\"schemaVersion\":1,\"tiers\":[%s]}";
        assertThrows(IllegalArgumentException.class, () -> parse(base.formatted("")), "No tiers");
        assertThrows(IllegalArgumentException.class, () -> parse("{\"schemaVersion\":2,\"tiers\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> parse(base.formatted(
                "{\"id\":\"a\",\"rankCredits\":11,\"rarities\":{\"common\":1}}")), "More credits than a player can earn");
        assertThrows(IllegalArgumentException.class, () -> parse(base.formatted(
                "{\"id\":\"a\",\"rankCredits\":1,\"rarities\":{}}")));
        assertThrows(IllegalArgumentException.class, () -> parse(base.formatted(
                "{\"id\":\"a\",\"rankCredits\":1,\"rarities\":{\"common\":0}}")));
        assertThrows(IllegalArgumentException.class, () -> parse(base.formatted(
                "{\"id\":\"a\",\"rankCredits\":1,\"rarities\":{\"mythic\":5}}")), "Unknown rarity ID");
        assertThrows(IllegalArgumentException.class, () -> parse(base.formatted(
                "{\"id\":\"A b\",\"rankCredits\":1,\"rarities\":{\"common\":5}}")));
        assertThrows(IllegalArgumentException.class, () -> parse(base.formatted(
                "{\"id\":\"a\",\"rankCredits\":1,\"rarities\":{\"common\":5}},{\"id\":\"a\",\"rankCredits\":1,\"rarities\":{\"rare\":5}}")));
        assertThrows(IllegalArgumentException.class, () -> parse(base.formatted(
                "{\"id\":\"a\",\"rankCredits\":1,\"rarities\":{\"common\":5},\"extra\":1}")));
    }

    @Test void generationIsDeterministicAndIndependentOfCallOrder() {
        var spec = EnemySpec.regular(tiers.tier("trial_rank_2"));
        var forward = new ArrayList<CombatSnapshot>();
        for (int i = 0; i < 50; i++) forward.add(generator.generate("trial-7f3a", i, spec, types));
        var reverse = new ArrayList<CombatSnapshot>();
        for (int i = 49; i >= 0; i--) reverse.add(generator.generate("trial-7f3a", i, spec, types));
        Collections.reverse(reverse);
        assertEquals(forward, reverse);
        assertEquals(forward.get(10).contentHash(), new EnemyGenerator(RankedRules.defaults())
                .generate("trial-7f3a", 10, spec, types).contentHash(), "A different generator instance agrees");
        assertTrue(forward.stream().map(CombatSnapshot::contentHash).distinct().count() > 40,
                "Different enemy indexes give different enemies");
        int identical = 0;
        for (int i = 0; i < 50; i++)
            if (forward.get(i).slots().equals(generator.generate("trial-9c01", i, spec, types).slots())) identical++;
        assertTrue(identical < 5, "A different encounter ID gives different enemies");
    }

    @Test void everyGeneratedEnemyFollowsThePlayerRules() {
        for (var tier : tiers.all()) {
            var spec = EnemySpec.regular(tier);
            var allowed = new HashSet<Rarity>();
            tier.rarities().forEach(w -> allowed.add(w.rarity()));
            for (int i = 0; i < 1500; i++) {
                var enemy = generator.generate("sweep-" + tier.id(), i, spec, types);
                rules.validate(enemy);
                assertEquals(CombatSnapshot.Source.ENEMY, enemy.source());
                assertTrue(allowed.contains(enemy.rarity()), "Rarity outside the tier's table");
                assertEquals(enemy.rarity().ordinal() + 1, enemy.slots().size());
                int capacity = enemy.slots().size() * (RankedAffix.RANKS - 1);
                int invested = enemy.slots().stream().mapToInt(s -> s.rank() - 1).sum();
                assertEquals(Math.min(tier.rankCredits(), capacity), invested, "Credits are spent exactly, up to capacity");
                assertNull(enemy.uniqueId(), "Regular enemies never get a Unique");
                for (var slot : enemy.slots()) {
                    assertTrue(rules.affix(slot.affixId()).band(slot.rank()).contains(slot.rolledValue()));
                    if (slot.affixId().equals("type_focus") || slot.affixId().equals("type_mastery"))
                        assertTrue(types.contains(slot.type()), "Species-typed affixes follow the enemy's own types");
                }
            }
        }
    }

    @Test void rarityFollowsTheTierWeights() {
        var tier = tiers.tier("trial_rank_1");
        var spec = EnemySpec.regular(tier);
        var counts = new EnumMap<Rarity, Integer>(Rarity.class);
        int samples = 20000;
        for (int i = 0; i < samples; i++) counts.merge(generator.generate("weights", i, spec, types).rarity(), 1, Integer::sum);
        for (var weight : tier.rarities())
            assertEquals(samples * weight.weight() / (double) tier.totalWeight(), counts.get(weight.rarity()), samples * 0.02,
                    weight.rarity().id());
        assertEquals(3, counts.size());
    }

    @Test void harderTiersProduceStrongerEnemiesOnAverage() {
        double previous = -1;
        for (var id : List.of("trial_rank_1", "trial_rank_2", "trial_rank_3", "boss")) {
            var spec = EnemySpec.regular(tiers.tier(id));
            double total = 0;
            int samples = 2000;
            for (int i = 0; i < samples; i++) {
                for (var slot : generator.generate("scaling-" + id, i, spec, types).slots()) total += slot.rolledValue();
            }
            assertTrue(total / samples > previous, id + " should out-scale the previous tier");
            previous = total / samples;
        }
    }

    @Test void unusedCreditsAreDroppedWhenEverySlotIsAtRankFive() {
        var tier = new EnemyTier("all_in", List.of(new EnemyTier.RarityWeight(Rarity.COMMON, 1)), 10);
        var enemy = generator.generate("one-slot", 0, EnemySpec.regular(tier), types);
        assertEquals(1, enemy.slots().size());
        assertEquals(5, enemy.slots().getFirst().rank());
        var none = new EnemyTier("no_ranks", List.of(new EnemyTier.RarityWeight(Rarity.MYTHICAL, 1)), 0);
        assertTrue(generator.generate("flat", 3, EnemySpec.regular(none), types).slots().stream().allMatch(s -> s.rank() == 1));
    }

    @Test void onlyBossesCarryAFixedDeclaredUnique() {
        var boss = EnemySpec.boss(tiers.tier("boss"), "ashen_heart");
        for (int i = 0; i < 100; i++) {
            var enemy = generator.generate("raid-boss", i, boss, types);
            assertEquals("ashen_heart", enemy.uniqueId());
            rules.validate(enemy);
        }
        assertThrows(IllegalArgumentException.class, () -> new EnemySpec(tiers.tier("trial_rank_1"), false, "ashen_heart"));
        assertThrows(IllegalArgumentException.class, () -> generator.generate("raid-boss", 0,
                EnemySpec.boss(tiers.tier("boss"), "not_a_unique"), types));
        assertNull(generator.generate("raid-boss", 0, EnemySpec.boss(tiers.tier("boss"), null), types).uniqueId());
    }

    @Test void invalidRequestsAreRefused() {
        var spec = EnemySpec.regular(tiers.tier("trial_rank_1"));
        assertThrows(IllegalArgumentException.class, () -> generator.generate("", 0, spec, types));
        assertThrows(IllegalArgumentException.class, () -> generator.generate("x".repeat(129), 0, spec, types));
        assertThrows(IllegalArgumentException.class, () -> generator.generate("ok", -1, spec, types));
        assertThrows(IllegalArgumentException.class, () -> generator.generate("ok", 0, spec, List.of("shadow")),
                "A species type outside the standard list is not silently accepted");
        assertThrows(IllegalArgumentException.class, () -> generator.generate("ok", 0, spec, List.of()));
    }

    @Test void playerAndEnemySnapshotsShareOneContractAndStayFrozen() {
        var progression = new RankedProgression(rules);
        var profile = progression.create(UUID.randomUUID(), UUID.randomUUID(), Rarity.EPIC, Origin.of("wild_capture"), 60, types, new Random(2));
        var player = CombatSnapshot.ofProfile(profile);
        rules.validate(player);
        assertEquals(CombatSnapshot.Source.PLAYER, player.source());
        assertEquals(profile.ordinarySlots(), player.slots());
        assertEquals(player.contentHash(), CombatSnapshot.ofProfile(profile).contentHash());

        var withUnique = progression.installUnique(profile, "ashen_heart", UUID.randomUUID()).profile();
        assertEquals("ashen_heart", CombatSnapshot.ofProfile(withUnique).uniqueId());
        assertNotEquals(player.contentHash(), CombatSnapshot.ofProfile(withUnique).contentHash());
        var upgraded = progression.upgrade(profile, "prefix:0", new Random(3)).profile();
        assertNotEquals(player.contentHash(), CombatSnapshot.ofProfile(upgraded).contentHash());

        assertThrows(UnsupportedOperationException.class, () -> player.slots().clear());
        var mutable = new ArrayList<>(player.slots());
        var copy = new CombatSnapshot(CombatSnapshot.Source.PLAYER, "x", Rarity.EPIC, 1, mutable, null);
        mutable.clear();
        assertEquals(player.slots().size(), copy.slots().size());
        assertThrows(IllegalArgumentException.class, () -> new CombatSnapshot(CombatSnapshot.Source.ENEMY, " ", Rarity.COMMON, 1, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new CombatSnapshot(CombatSnapshot.Source.ENEMY, "ok", Rarity.COMMON, 1, List.of(), "Bad Id"));
    }

    @Test void snapshotValidationRejectsTamperingAndTooManyRanks() {
        var enemy = generator.generate("tamper", 1, EnemySpec.regular(tiers.tier("trial_rank_3")), types);
        var slot = enemy.slots().getFirst();
        var outOfBand = new ArrayList<>(enemy.slots());
        outOfBand.set(0, new OrdinarySlot(slot.slotId(), slot.category(), slot.rank(), slot.affixId(), slot.parameters(), 100, slot.definitionVersion()));
        assertThrows(IllegalArgumentException.class, () -> rules.validate(new CombatSnapshot(
                CombatSnapshot.Source.ENEMY, "tamper#1", enemy.rarity(), 1, outOfBand, null)));
        assertThrows(IllegalArgumentException.class, () -> rules.validate(new CombatSnapshot(
                CombatSnapshot.Source.ENEMY, "tamper#1", enemy.rarity(), 2, enemy.slots(), null)), "Wrong content version");
        assertThrows(IllegalArgumentException.class, () -> rules.validate(new CombatSnapshot(
                CombatSnapshot.Source.ENEMY, "tamper#1", Rarity.COMMON, 1, enemy.slots(), null)), "Slots do not match rarity");
        assertThrows(IllegalArgumentException.class, () -> rules.validate(new CombatSnapshot(
                CombatSnapshot.Source.ENEMY, "tamper#1", enemy.rarity(), 1, enemy.slots(), "unknown_power")));
    }
}
