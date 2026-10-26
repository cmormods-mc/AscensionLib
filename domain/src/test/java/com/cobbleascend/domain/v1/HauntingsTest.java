package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HauntingsTest {

    @Test void thereAreTenWithTwoRaresAndIdsAreUniqueAndMatchTheShowdownModule() {
        var all = Hauntings.all();
        assertEquals(10, all.size());
        assertEquals(2, all.stream().filter(Hauntings.Haunting::rare).count());
        var ids = new HashSet<String>();
        for (var haunting : all) {
            assertTrue(ids.add(haunting.id()), "duplicate id " + haunting.id());
            assertFalse(haunting.name().isBlank());
            assertFalse(haunting.effect().isBlank());
        }
        assertEquals(Set.of("wisp", "hex", "spectral_veil", "pumpkin_lantern", "trick_or_treat", "cobweb", "graveyard_chill",
                "midnight_howl", "headless_rider", "reapers_toll"), ids);
    }

    @Test void theCommonWeightsAddUpToOneHundredAndTheRareOnesToTheRarePermille() {
        int commons = Hauntings.all().stream().filter(h -> !h.rare()).mapToInt(Hauntings.Haunting::weight).sum();
        int rares = Hauntings.all().stream().filter(Hauntings.Haunting::rare).mapToInt(Hauntings.Haunting::weight).sum();
        assertEquals(100, commons);
        assertEquals(Hauntings.RARE_PERMILLE, rares);
    }

    @Test void aSeedAlwaysGivesTheSameHaunting() {
        for (long seed = 0; seed < 500; seed++) assertSame(Hauntings.roll(seed), Hauntings.roll(seed));
    }

    @Test void overManyRollsEveryHauntingAppearsAtAboutItsOdds() {
        int rolls = 200_000;
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < rolls; i++) seen.merge(Hauntings.roll(i * 7919L + 13).id(), 1, Integer::sum);
        for (var haunting : Hauntings.all()) {
            double expected = haunting.rare()
                    ? haunting.weight() / 1000.0
                    : (1000 - Hauntings.RARE_PERMILLE) / 1000.0 * haunting.weight() / 100.0;
            double actual = seen.getOrDefault(haunting.id(), 0) / (double) rolls;
            assertEquals(expected, actual, expected * 0.2 + 0.002, haunting.id());
        }
        double rare = (seen.getOrDefault("headless_rider", 0) + seen.getOrDefault("reapers_toll", 0)) / (double) rolls;
        assertEquals(0.04, rare, 0.004, "about four rolls in a hundred are Rare");
    }

    @Test void theRollCostsStepUpAndThenStayAtEight() {
        assertEquals(1, Hauntings.cost(0));
        assertEquals(3, Hauntings.cost(1));
        assertEquals(5, Hauntings.cost(2));
        assertEquals(8, Hauntings.cost(3));
        assertEquals(8, Hauntings.cost(4));
        assertEquals(8, Hauntings.cost(400));
        assertThrows(IllegalArgumentException.class, () -> Hauntings.cost(-1));
    }

    @Test void theSeedIsStableAndDependsOnBothTheOperationAndThePokemon() {
        var op = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var pokemon = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(Hauntings.seed(op, pokemon), Hauntings.seed(op, pokemon));
        assertNotEquals(Hauntings.seed(op, pokemon), Hauntings.seed(UUID.randomUUID(), pokemon));
        assertNotEquals(Hauntings.seed(op, pokemon), Hauntings.seed(op, UUID.randomUUID()));
    }

    @Test void lookupByIdIsExactAndUnknownIsEmpty() {
        assertEquals("Wisp", Hauntings.byId("wisp").orElseThrow().name());
        assertTrue(Hauntings.byId("headless_rider").orElseThrow().rare());
        assertTrue(Hauntings.byId("Wisp").isEmpty());
        assertTrue(Hauntings.byId(null).isEmpty());
    }
}
