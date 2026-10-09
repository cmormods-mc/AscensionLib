package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.Rarity;
import com.cobbleascend.domain.v1.CraftException.Reason;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RankedProgressionTest {
    private final RankedRules rules = RankedRules.defaults();
    private final RankedProgression progression = new RankedProgression(rules);
    private final List<String> types = List.of("fire", "flying");
    private final UUID authority = UUID.randomUUID();

    private ProfileV1 create(Rarity rarity, int level, Random random) {
        return progression.create(UUID.randomUUID(), authority, rarity, Origin.of("wild_capture"), level, types, random);
    }

    private static Reason reasonOf(Runnable action) {
        return assertThrows(CraftException.class, action::run).reason();
    }

    private RankedRules rulesWithSecondUnique() {
        var bands = new LinkedHashMap<String, List<RankBand>>();
        rules.affixes().forEach(a -> bands.put(a.id(), a.bands()));
        return new RankedRules(rules.base(), rules.catalogVersion(), bands,
                List.of(new UniqueDefinition("ashen_heart", "Ashen Heart", rules.catalogVersion()),
                        new UniqueDefinition("second_wind", "Second Wind", rules.catalogVersion())));
    }

    @ParameterizedTest @EnumSource(Rarity.class)
    void everyRarityCreatesFilledRankOneProfilesWithStableSlotIds(Rarity rarity) {
        var random = new Random(12345);
        for (int n = 0; n < 500; n++) {
            var profile = create(rarity, 1, random);
            rules.validate(profile);
            assertEquals(rarity.ordinal() + 1, profile.ordinarySlots().size());
            assertEquals(profile.ordinarySlots().size(),
                    profile.ordinarySlots().stream().map(s -> rules.affix(s.affixId()).family()).distinct().count());
            assertNull(profile.unique(), "A capture draw never creates a Unique");
            for (var category : Category.values()) {
                for (int i = 0; i < profile.slotCount(category); i++)
                    assertTrue(profile.slot(OrdinarySlot.slotId(category, i)).isPresent());
            }
            for (var slot : profile.ordinarySlots()) {
                assertEquals(1, slot.rank());
                assertTrue(rules.affix(slot.affixId()).band(1).contains(slot.rolledValue()));
                if (slot.affixId().equals("type_focus") || slot.affixId().equals("type_mastery"))
                    assertTrue(types.contains(slot.type()));
                if (slot.affixId().equals("type_ward") || slot.affixId().equals("type_bulwark"))
                    assertTrue(com.cobbleascend.domain.Rules.TYPES.contains(slot.type()));
            }
        }
    }

    @Test void seededCreationAndCraftingReproduce() {
        var id = UUID.randomUUID();
        var a = progression.create(id, authority, Rarity.MYTHICAL, Origin.of("admin"), 100, types, new Random(4));
        var b = progression.create(id, authority, Rarity.MYTHICAL, Origin.of("admin"), 100, types, new Random(4));
        assertEquals(a.ordinarySlots(), b.ordinarySlots());
        assertEquals(progression.upgrade(a, "prefix:0", new Random(9)).profile().ordinarySlots(),
                progression.upgrade(b, "prefix:0", new Random(9)).profile().ordinarySlots());
    }

    @Test void milestoneCreditsMatchTheSpecificationExamples() {
        var random = new Random(1);
        var level9 = create(Rarity.COMMON, 9, random);
        assertEquals(0, level9.pendingCredits());
        var level10 = progression.observeLevel(level9, 10);
        assertEquals(List.of(10), level10.newlyAwarded());
        assertEquals(1, level10.profile().pendingCredits());

        var level19 = create(Rarity.COMMON, 19, random);
        assertEquals(1, level19.pendingCredits());
        var level31 = progression.observeLevel(level19, 31);
        assertEquals(List.of(20, 30), level31.newlyAwarded());
        assertEquals(3, level31.profile().pendingCredits());

        assertEquals(3, create(Rarity.COMMON, 37, random).pendingCredits());
        var level100 = create(Rarity.MYTHICAL, 100, random);
        assertEquals(10, level100.pendingCredits());
        assertEquals(0, level100.spentUpgradeCredits());
        assertTrue(level100.ordinarySlots().stream().allMatch(s -> s.rank() == 1));
        var beyond = progression.observeLevel(level100, 250);
        assertSame(level100, beyond.profile(), "Levels above 100 clamp and award nothing new");
        assertEquals(10, create(Rarity.COMMON, 250, random).awardedMilestones().size());
        assertEquals(Reason.INVALID_LEVEL, reasonOf(() -> progression.observeLevel(level100, 0)));
    }

    @Test void loweredReplayedAndRestoredLevelsNeverAwardTwice() {
        var random = new Random(2);
        var start = create(Rarity.UNCOMMON, 50, random);
        assertSame(start, progression.observeLevel(start, 20).profile());
        assertSame(start, progression.observeLevel(start, 50).profile());
        var spent = progression.upgrade(start, "prefix:0", random).profile();
        var lowered = progression.observeLevel(spent, 5);
        assertSame(spent, lowered.profile());
        var restored = progression.observeLevel(spent, 50);
        assertSame(spent, restored.profile());
        assertEquals(4, restored.profile().pendingCredits());
        var higher = progression.observeLevel(spent, 51);
        assertEquals(List.of(), higher.newlyAwarded());
        assertEquals(51, higher.profile().highestLevelObserved());
        assertEquals(spent.revision() + 1, higher.profile().revision());
        assertEquals(4, higher.profile().pendingCredits());
        var more = progression.observeLevel(higher.profile(), 60);
        assertEquals(List.of(60), more.newlyAwarded());
        assertEquals(5, more.profile().pendingCredits());
    }

    @Test void upgradeAdvancesOneRankIntoAStrictlyHigherBand() {
        var random = new Random(3);
        for (int round = 0; round < 200; round++) {
            var profile = create(Rarity.MYTHICAL, 100, random);
            while (profile.pendingCredits() > 0) {
                var slotId = profile.ordinarySlots().get(random.nextInt(profile.ordinarySlots().size())).slotId();
                var before = profile.slot(slotId).orElseThrow();
                if (before.rank() == 5) continue;
                var result = progression.upgrade(profile, slotId, random);
                var after = result.profile().slot(slotId).orElseThrow();
                assertEquals(before.rank() + 1, after.rank());
                assertEquals(before.affixId(), after.affixId());
                assertEquals(before.parameters(), after.parameters());
                assertEquals(before.category(), after.category());
                assertTrue(rules.affix(after.affixId()).band(after.rank()).contains(after.rolledValue()));
                assertTrue(after.rolledValue() > before.rolledValue(), "Upgrading never lowers raw magnitude");
                assertEquals(1, result.creditsSpent());
                assertEquals(Map.of(), result.cost());
                assertEquals(profile.pendingCredits() - 1, result.profile().pendingCredits());
                assertEquals(profile.revision() + 1, result.profile().revision());
                for (var other : profile.ordinarySlots())
                    if (!other.slotId().equals(slotId)) assertEquals(other, result.profile().slot(other.slotId()).orElseThrow());
                profile = result.profile();
            }
            assertEquals(10, profile.spentUpgradeCredits());
        }
    }

    @Test void everySlotReachesRankFiveInEveryBand() {
        var random = new Random(4);
        var profile = create(Rarity.COMMON, 100, random);
        for (int rank = 2; rank <= 5; rank++) {
            profile = progression.upgrade(profile, "prefix:0", random).profile();
            assertEquals(rank, profile.slot("prefix:0").orElseThrow().rank());
        }
        assertEquals(6, profile.pendingCredits());
    }

    @Test void unusableCreditsAreRetainedForFutureSlots() {
        var random = new Random(5);
        var profile = create(Rarity.COMMON, 100, random);
        for (int i = 0; i < 4; i++) profile = progression.upgrade(profile, "prefix:0", random).profile();
        var maxed = profile;
        assertEquals(6, maxed.pendingCredits());
        assertEquals(Reason.MAX_RANK, reasonOf(() -> progression.upgrade(maxed, "prefix:0", random)));
        assertEquals(6, maxed.pendingCredits());

        var promoted = progression.promote(maxed.withAttunement(3), types, random).profile();
        assertEquals(Rarity.UNCOMMON, promoted.rarity());
        assertEquals(6, promoted.pendingCredits());
        assertEquals(5, promoted.slot("prefix:0").orElseThrow().rank());
        var usable = progression.upgrade(promoted, "suffix:0", random).profile();
        assertEquals(2, usable.slot("suffix:0").orElseThrow().rank());
        assertEquals(5, usable.pendingCredits());
    }

    @Test void upgradeRefusalsAreStructured() {
        var random = new Random(6);
        var none = create(Rarity.RARE, 9, random);
        assertEquals(Reason.NO_PENDING_CREDIT, reasonOf(() -> progression.upgrade(none, "prefix:0", random)));
        var some = create(Rarity.RARE, 40, random);
        assertEquals(Reason.UNKNOWN_SLOT, reasonOf(() -> progression.upgrade(some, "prefix:2", random)));
        assertEquals(Reason.UNKNOWN_SLOT, reasonOf(() -> progression.upgrade(some, "suffix:1", random)));
        assertEquals(Reason.UNKNOWN_SLOT, reasonOf(() -> progression.upgrade(some, "unique", random)));
        assertEquals(Reason.UNKNOWN_SLOT, reasonOf(() -> progression.reforge(some, "unique", types, random)));
        assertEquals(Reason.UNKNOWN_SLOT, reasonOf(() -> progression.refine(some, "unique", random)));
    }

    @Test void reforgeKeepsSlotRankAndCreditsAtEveryRankAndNeverDuplicatesFamilies() {
        var random = new Random(12);
        var profile = create(Rarity.MYTHICAL, 100, random);
        for (int i = 0; i < 4; i++) profile = progression.upgrade(profile, "prefix:0", random).profile();
        for (int i = 0; i < 2; i++) profile = progression.upgrade(profile, "suffix:1", random).profile();
        profile = progression.upgrade(profile, "prefix:2", random).profile();
        var cost = Map.of(MaterialId.RESONANCE_DUST, 24L, MaterialId.FACET, 1L);
        for (int n = 0; n < 900; n++) {
            var slotId = profile.ordinarySlots().get(n % profile.ordinarySlots().size()).slotId();
            var before = profile.slot(slotId).orElseThrow();
            var result = progression.reforge(profile, slotId, types, random);
            var after = result.profile().slot(slotId).orElseThrow();
            assertEquals(before.rank(), after.rank());
            assertEquals(before.category(), after.category());
            assertTrue(rules.affix(after.affixId()).band(after.rank()).contains(after.rolledValue()));
            assertEquals(cost, result.cost());
            assertEquals(0, result.creditsSpent());
            assertEquals(profile.pendingCredits(), result.profile().pendingCredits());
            assertEquals(profile.spentUpgradeCredits(), result.profile().spentUpgradeCredits());
            for (var other : profile.ordinarySlots())
                if (!other.slotId().equals(slotId)) assertEquals(other, result.profile().slot(other.slotId()).orElseThrow());
            rules.validate(result.profile());
            profile = result.profile();
        }
    }

    @Test void reforgeCanLandOnEveryAffixOfTheCategoryIncludingTheSameOne() {
        var random = new Random(13);
        var profile = create(Rarity.UNCOMMON, 1, random);
        for (var category : Category.values()) {
            var slotId = OrdinarySlot.slotId(category, 0);
            var counts = new HashMap<String, Integer>();
            for (int n = 0; n < 40000; n++) counts.merge(progression.reforge(profile, slotId, types, random)
                    .profile().slot(slotId).orElseThrow().affixId(), 1, Integer::sum);
            var expected = rules.affixes().stream().filter(a -> a.category() == category).toList();
            assertEquals(expected.size(), counts.size(), "Every affix is reachable, including the rarest");
        }
    }

    @Test void weightsMakePowerhousesRareAndStandardAffixesCommon() {
        var random = new Random(14);
        var profile = create(Rarity.COMMON, 1, random);
        var counts = new HashMap<String, Integer>();
        int draws = 100_000;
        for (int n = 0; n < draws; n++) counts.merge(progression.reforge(profile, "prefix:0", types, random)
                .profile().slot("prefix:0").orElseThrow().affixId(), 1, Integer::sum);
        // Prefix weights total 1021: type_mastery 8, overwhelming_force 3, the three status affixes and Keen Edge and Ensnaring 40 each, Momentum and Swift Strike 30 each, standard templates 100 each.
        assertEquals(draws * 8.0 / 1021, counts.get("type_mastery"), draws * 0.003);
        assertEquals(draws * 3.0 / 1021, counts.get("overwhelming_force"), draws * 0.002);
        assertEquals(draws * 100.0 / 1021, counts.get("type_focus"), draws * 0.01);
        assertTrue(counts.get("overwhelming_force") < counts.get("type_mastery"));
        assertTrue(counts.get("type_mastery") * 5 < counts.get("physical_force"));
    }

    @Test void refineKeepsAffixParametersAndRankAndCoversTheWholeBand() {
        var random = new Random(9);
        var profile = create(Rarity.COMMON, 100, random);
        profile = progression.upgrade(progression.upgrade(profile, "prefix:0", random).profile(), "prefix:0", random).profile();
        var old = profile.slot("prefix:0").orElseThrow();
        assertEquals(3, old.rank());
        var band = rules.affix(old.affixId()).band(3);
        var outcomes = new HashSet<Integer>();
        for (int n = 0; n < 600; n++) {
            var result = progression.refine(profile, "prefix:0", random);
            var roll = result.profile().slot("prefix:0").orElseThrow();
            assertEquals(old.affixId(), roll.affixId());
            assertEquals(old.parameters(), roll.parameters());
            assertEquals(3, roll.rank());
            assertEquals(Map.of(MaterialId.RESONANCE_DUST, 12L), result.cost());
            assertEquals(profile.pendingCredits(), result.profile().pendingCredits());
            outcomes.add(roll.rolledValue());
        }
        assertEquals(band.max() - band.min() + 1, outcomes.size());
        assertTrue(outcomes.contains(old.rolledValue()), "Refining may yield the same value");
    }

    @Test void promotionAddsExactlyOneRankOneSlotAndRetainsEverythingElse() {
        var random = new Random(21);
        var profile = create(Rarity.COMMON, 70, random);
        profile = progression.upgrade(profile, "prefix:0", random).profile();
        profile = progression.installUnique(profile, "ashen_heart", UUID.randomUUID()).profile().withAttunement(90);
        long dust = 0, facets = 0, cores = 0;
        while (profile.rarity() != Rarity.MYTHICAL) {
            var result = progression.promote(profile, types, random);
            var next = result.profile();
            assertEquals(profile.ordinarySlots(), next.ordinarySlots().subList(0, profile.ordinarySlots().size()));
            assertEquals(profile.ordinarySlots().size() + 1, next.ordinarySlots().size());
            var added = next.ordinarySlots().getLast();
            assertEquals(1, added.rank());
            assertEquals(profile.slotCount(added.category()), added.index());
            assertEquals(profile.unique(), next.unique());
            assertEquals(profile.awardedMilestones(), next.awardedMilestones());
            assertEquals(profile.spentUpgradeCredits(), next.spentUpgradeCredits());
            assertEquals(profile.pendingCredits(), next.pendingCredits());
            assertEquals(90, next.attunement());
            assertEquals(Rarity.COMMON, next.initialRarity());
            assertEquals(profile.revision() + 1, next.revision());
            dust += result.cost().getOrDefault(MaterialId.RESONANCE_DUST, 0L);
            facets += result.cost().getOrDefault(MaterialId.FACET, 0L);
            cores += result.cost().getOrDefault(MaterialId.ASCENSION_CORE, 0L);
            profile = next;
        }
        assertEquals(List.of(770L, 37L, 7L), List.of(dust, facets, cores));
        var maxed = profile;
        assertEquals(Reason.MAX_RARITY, reasonOf(() -> progression.promote(maxed, types, random)));
    }

    @Test void promotionRequiresAttunementAndDoesNotMutateInput() {
        var random = new Random(22);
        var profile = create(Rarity.COMMON, 1, random);
        assertEquals(Reason.INSUFFICIENT_ATTUNEMENT, reasonOf(() -> progression.promote(profile.withAttunement(2), types, random)));
        assertEquals(Rarity.UNCOMMON, progression.promote(profile.withAttunement(3), types, random).profile().rarity());
        assertEquals(Rarity.COMMON, profile.rarity());
    }

    @Test void uniqueInstallAndReplaceAreExplicitSingleCatalystTransitions() {
        var random = new Random(30);
        var multi = new RankedProgression(rulesWithSecondUnique());
        var profile = multi.create(UUID.randomUUID(), authority, Rarity.EPIC, Origin.of("wild_capture"), 40, types, random);
        assertEquals(2, multi.eligibleUniques(profile).size());
        var operation = UUID.randomUUID();
        var installed = multi.installUnique(profile, "ashen_heart", operation);
        assertEquals(Map.of(MaterialId.UNIQUE_CATALYST, 1L), installed.cost());
        assertEquals(new UniqueInstance("ashen_heart", 1, operation), installed.profile().unique());
        assertEquals(profile.ordinarySlots(), installed.profile().ordinarySlots());
        assertEquals(profile.pendingCredits(), installed.profile().pendingCredits());
        assertEquals(profile.revision() + 1, installed.profile().revision());
        assertEquals(List.of("second_wind"), multi.eligibleUniques(installed.profile()).stream().map(UniqueDefinition::id).toList());

        var current = installed.profile();
        assertEquals(Reason.UNIQUE_PRESENT, reasonOf(() -> multi.installUnique(current, "second_wind", UUID.randomUUID())));
        assertEquals(Reason.SAME_UNIQUE, reasonOf(() -> multi.replaceUnique(current, "ashen_heart", UUID.randomUUID())));
        assertEquals(Reason.UNKNOWN_UNIQUE, reasonOf(() -> multi.replaceUnique(current, "missing", UUID.randomUUID())));
        assertEquals(Reason.NO_UNIQUE, reasonOf(() -> multi.replaceUnique(profile, "second_wind", UUID.randomUUID())));
        assertEquals(Reason.UNKNOWN_UNIQUE, reasonOf(() -> multi.installUnique(profile, "missing", UUID.randomUUID())));

        var replaceOperation = UUID.randomUUID();
        var replaced = multi.replaceUnique(current, "second_wind", replaceOperation);
        assertEquals(Map.of(MaterialId.UNIQUE_CATALYST, 1L), replaced.cost());
        assertEquals(new UniqueInstance("second_wind", 1, replaceOperation), replaced.profile().unique());
        assertEquals(current.ordinarySlots(), replaced.profile().ordinarySlots());
    }

    @Test void ordinaryCraftingAndUpgradesNeverTouchTheUnique() {
        var random = new Random(31);
        var profile = progression.installUnique(create(Rarity.RARE, 100, random), "ashen_heart", UUID.randomUUID()).profile();
        var unique = profile.unique();
        for (int n = 0; n < 200; n++) {
            var slot = profile.ordinarySlots().get(n % profile.ordinarySlots().size()).slotId();
            profile = switch (n % 3) {
                case 0 -> progression.reforge(profile, slot, types, random).profile();
                case 1 -> progression.refine(profile, slot, random).profile();
                default -> profile.pendingCredits() > 0 && profile.slot(slot).orElseThrow().rank() < 5
                        ? progression.upgrade(profile, slot, random).profile() : profile;
            };
            assertEquals(unique, profile.unique());
        }
    }

    @Test void uniqueIsAvailableOnEveryRarity() {
        for (var rarity : Rarity.values()) {
            var profile = create(rarity, 1, new Random(rarity.ordinal()));
            assertEquals(1, progression.installUnique(profile, "ashen_heart", UUID.randomUUID()).cost().size());
        }
    }

    @Test void randomOperationSequencesPreserveEveryInvariant() {
        for (long seed = 1; seed <= 6; seed++) {
            var random = new Random(seed);
            var profile = create(Rarity.COMMON, 1, random).withAttunement(90);
            var multiRules = rulesWithSecondUnique();
            var multi = new RankedProgression(multiRules);
            var identity = profile;
            for (int step = 0; step < 1500; step++) {
                var previous = profile;
                try {
                    var slot = previous.ordinarySlots().get(random.nextInt(previous.ordinarySlots().size())).slotId();
                    profile = switch (random.nextInt(8)) {
                        case 0 -> multi.observeLevel(previous, 1 + random.nextInt(110)).profile();
                        case 1, 2 -> multi.upgrade(previous, slot, random).profile();
                        case 3 -> multi.reforge(previous, slot, types, random).profile();
                        case 4 -> multi.refine(previous, slot, random).profile();
                        case 5 -> multi.promote(previous, types, random).profile();
                        case 6 -> multi.installUnique(previous, "ashen_heart", UUID.randomUUID()).profile();
                        default -> multi.replaceUnique(previous, random.nextBoolean() ? "ashen_heart" : "second_wind", UUID.randomUUID()).profile();
                    };
                } catch (CraftException refused) {
                    assertNotNull(refused.reason());
                    continue;
                }
                multiRules.validate(profile);
                assertEquals(identity.profileId(), profile.profileId());
                assertEquals(identity.pokemonId(), profile.pokemonId());
                assertEquals(identity.initialRarity(), profile.initialRarity());
                assertTrue(profile.revision() >= previous.revision());
                assertTrue(profile.highestLevelObserved() >= previous.highestLevelObserved());
                assertTrue(profile.awardedMilestones().containsAll(previous.awardedMilestones()));
                assertTrue(profile.pendingCredits() >= 0);
                assertEquals(profile.awardedMilestones().size() - profile.spentUpgradeCredits(), profile.pendingCredits());
                assertEquals(profile.ordinarySlots().stream().mapToInt(s -> s.rank() - 1).sum(), profile.spentUpgradeCredits());
                for (var old : previous.ordinarySlots())
                    assertTrue(profile.slot(old.slotId()).orElseThrow().rank() >= old.rank(), "Ranks never decrease");
                assertTrue(profile.rarity().ordinal() >= previous.rarity().ordinal());
            }
        }
    }
}
