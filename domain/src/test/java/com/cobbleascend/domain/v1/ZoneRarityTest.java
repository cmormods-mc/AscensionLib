package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cobbleascend.domain.Rarity;
import com.cobbleascend.domain.Rules;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The per-tier rarity table the owner approved on 2026-10-16 (docs/ALL-WILD-PROFILES-DESIGN.md). */
class ZoneRarityTest {
    private static final int[] ORDINARY = {5500, 2800, 1200, 400, 90, 10};

    /** The approved table, percent, tiers 1 to 10, Common to Mythic. */
    private static final double[][] TABLE = {
            {55.00, 28.00, 12.00, 4.00, 0.90, 0.10}, {42.99, 29.54, 17.09, 7.69, 2.34, 0.35}, {30.22, 28.03, 21.89, 13.30, 5.45, 1.10},
            {18.46, 23.12, 24.37, 19.98, 11.06, 3.02}, {9.54, 16.13, 22.95, 25.40, 18.97, 7.00}, {4.15, 9.47, 18.18, 27.17, 27.39, 13.64},
            {1.54, 4.75, 12.32, 24.84, 33.81, 22.73}, {0.50, 2.10, 7.34, 19.99, 36.73, 33.33}, {0.15, 0.84, 3.97, 14.58, 36.17, 44.30},
            {0.04, 0.31, 1.99, 9.88, 33.08, 54.69}};

    @Test
    @DisplayName("the ordinary table in the rules is the one the zone table starts from")
    void theBaseTableIsTheOrdinaryOne() {
        var rules = Rules.defaults();
        for (var rarity : Rarity.values()) assertEquals(ORDINARY[rarity.ordinal()], rules.tier(rarity).weight());
    }

    @Test
    @DisplayName("tier 1 is the ordinary weights exactly, and a draw from them is the same draw as Rules.rollRarity")
    void tierOneIsTheOrdinaryTable() {
        assertArrayEquals(ORDINARY, ZoneRarity.weights(1, ORDINARY, ZoneSettings.DEFAULTS));
        var rules = Rules.defaults();
        var a = new SplittableRandom(5);
        var b = new SplittableRandom(5);
        for (int i = 0; i < 2000; i++) assertEquals(rules.rollRarity(a), ZoneRarity.roll(b, ORDINARY), "draw " + i);
    }

    @Test
    @DisplayName("every tier from 1 to 10 matches the approved table to within a hundredth of a point and adds up to exactly one million tickets")
    void theApprovedTable() {
        for (int tier = 1; tier <= 10; tier++) {
            var weights = ZoneRarity.weights(tier, ORDINARY, ZoneSettings.DEFAULTS);
            if (tier > 1) assertEquals(ZoneRarity.SCALE, java.util.Arrays.stream(weights).sum(), "tier " + tier);
            var percent = ZoneRarity.percentages(weights);
            for (int i = 0; i < 6; i++) assertEquals(TABLE[tier - 1][i], percent[i], 0.011, "tier " + tier + " rarity " + i);
        }
    }

    @Test
    @DisplayName("a tilt of 0 keeps every tier ordinary; a larger tilt makes the top tier rarer still")
    void theTiltIsTheDial() {
        var flat = new ZoneSettings(1, Map.of(), 0, Map.of());
        assertArrayEquals(ORDINARY, ZoneRarity.weights(9, ORDINARY, flat));
        var steep = new ZoneSettings(1, Map.of(), 0.5, Map.of());
        assertEquals(true, ZoneRarity.percentages(ZoneRarity.weights(6, ORDINARY, steep))[5] > ZoneRarity.percentages(ZoneRarity.weights(6, ORDINARY, ZoneSettings.DEFAULTS))[5]);
    }

    @Test
    @DisplayName("an explicit weight list replaces the tilt for its tier only")
    void anExplicitTierWins() {
        var settings = new ZoneSettings(1, Map.of(), 0.30, Map.of(3, List.of(1, 1, 1, 1, 1, 1)));
        assertArrayEquals(new int[] {1, 1, 1, 1, 1, 1}, ZoneRarity.weights(3, ORDINARY, settings));
        assertArrayEquals(ZoneRarity.weights(4, ORDINARY, ZoneSettings.DEFAULTS), ZoneRarity.weights(4, ORDINARY, settings));
        RandomGenerator fixed = new SplittableRandom(1);
        for (int i = 0; i < 50; i++) ZoneRarity.roll(fixed, new int[] {0, 0, 0, 0, 0, 7});
        assertEquals(Rarity.MYTHICAL, ZoneRarity.roll(fixed, new int[] {0, 0, 0, 0, 0, 7}));
    }

    @Test
    @DisplayName("settings that do not fit are refused with a reason")
    void badSettingsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ZoneSettings(11, Map.of(), 0.3, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ZoneSettings(1, Map.of("Not A Dimension", 2), 0.3, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ZoneSettings(1, Map.of("minecraft:the_nether", 11), 0.3, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ZoneSettings(1, Map.of(), -1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ZoneSettings(1, Map.of(), 0.3, Map.of(2, List.of(1, 2, 3))));
        assertThrows(IllegalArgumentException.class, () -> new ZoneSettings(1, Map.of(), 0.3, Map.of(2, List.of(0, 0, 0, 0, 0, 0))));
    }
}
