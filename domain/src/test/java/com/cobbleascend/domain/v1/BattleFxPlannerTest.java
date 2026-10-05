package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.BattleFxPlanner.Actor;
import com.cobbleascend.domain.v1.BattleFxPlanner.Combatant;
import com.cobbleascend.domain.v1.BattleFxPlanner.Kind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BattleFxPlannerTest {
    private final RankedRules rules = RankedRules.defaults();
    private final EnemyGenerator generator = new EnemyGenerator(rules);
    private final EnemyTier tier = EnemyTiers.defaults().tier("trial_rank_3");

    private CombatSnapshot snapshot(String id) {
        return generator.generate(id, 0, EnemySpec.regular(tier), List.of("fire"));
    }

    private static Combatant mon(String effected) { return new Combatant(effected, UUID.nameUUIDFromBytes(effected.getBytes())); }

    private static Optional<CombatSnapshot> none(UUID id) { return Optional.empty(); }

    private final Combatant mine = mon("mine");
    private final Combatant wildOne = mon("wild");
    private final Combatant boss = mon("boss");

    @Test void aPlayersPokemonActsFromItsProfileAndOneWithoutDoesNot() {
        var other = mon("other");
        var actors = List.of(new Actor(Kind.PLAYER, List.of(mine, other)));
        var plan = BattleFxPlanner.plan(actors, false, false, null,
                id -> id.equals(mine.originalId()) ? Optional.of(snapshot("p")) : Optional.empty(), BattleFxPlannerTest::none);
        assertEquals(java.util.Set.of("mine"), plan.keySet());
    }

    @Test void pvpNeverActs() {
        var actors = List.of(new Actor(Kind.PLAYER, List.of(mine)), new Actor(Kind.PLAYER, List.of(mon("foe"))));
        var plan = BattleFxPlanner.plan(actors, true, false, null, id -> Optional.of(snapshot("p")), id -> Optional.of(snapshot("w")));
        assertTrue(plan.isEmpty());
    }

    @Test void aWildPokemonActsFromItsDerivedRating() {
        var actors = List.of(new Actor(Kind.PLAYER, List.of(mine)), new Actor(Kind.WILD, List.of(wildOne)));
        var plan = BattleFxPlanner.plan(actors, false, false, null, BattleFxPlannerTest::none, id -> Optional.of(snapshot("w")));
        assertEquals(java.util.Set.of("wild"), plan.keySet());
    }

    @Test void aTrainerIsNativeUnlessAnEncounterIsArmed() {
        var actors = List.of(new Actor(Kind.PLAYER, List.of(mine)), new Actor(Kind.NPC, List.of(mon("trainer"))));
        assertTrue(BattleFxPlanner.plan(actors, false, false, null, BattleFxPlannerTest::none, id -> Optional.of(snapshot("w"))).isEmpty());
        var armed = BattleFxPlanner.plan(actors, false, false, List.of(snapshot("e")), BattleFxPlannerTest::none, BattleFxPlannerTest::none);
        assertEquals(java.util.Set.of("trainer"), armed.keySet());
    }

    @Test void anArmedEncounterOverridesTheWildRatingAndItsEnemyIsTheDeclaredOne() {
        var declared = snapshot("declared");
        var actors = List.of(new Actor(Kind.PLAYER, List.of(mine)), new Actor(Kind.WILD, List.of(wildOne)));
        var plan = BattleFxPlanner.plan(actors, false, false, List.of(declared), BattleFxPlannerTest::none,
                id -> Optional.of(snapshot("not-this")));
        assertEquals(BattleFx.effectsOf(declared), plan.get("wild"));
    }

    @Test void anArmedEncounterWithNoEnemiesMeansExplicitlyNative() {
        var actors = List.of(new Actor(Kind.PLAYER, List.of(mine)), new Actor(Kind.WILD, List.of(wildOne)));
        var plan = BattleFxPlanner.plan(actors, false, false, List.of(), BattleFxPlannerTest::none, id -> Optional.of(snapshot("w")));
        assertTrue(plan.isEmpty(), "an Echo duel armed as native gets no wild rating");
    }

    @Test void aRaidBossIsNeverRatedAsWildButTakesItsDeclaredEnemy() {
        var actors = List.of(new Actor(Kind.PLAYER, List.of(mine)), new Actor(Kind.WILD, List.of(boss)));
        assertTrue(BattleFxPlanner.plan(actors, false, true, null, BattleFxPlannerTest::none, id -> Optional.of(snapshot("w"))).isEmpty());
        var declared = snapshot("boss");
        var plan = BattleFxPlanner.plan(actors, false, true, List.of(declared), BattleFxPlannerTest::none, BattleFxPlannerTest::none);
        assertEquals(BattleFx.effectsOf(declared), plan.get("boss"));
    }

    @Test void fewerDeclaredEnemiesThanOpponentsLeavesTheRestNative() {
        var second = mon("second");
        var actors = List.of(new Actor(Kind.PLAYER, List.of(mine)), new Actor(Kind.WILD, List.of(wildOne, second)));
        var plan = BattleFxPlanner.plan(actors, false, false, List.of(snapshot("only")), BattleFxPlannerTest::none, BattleFxPlannerTest::none);
        assertEquals(java.util.Set.of("wild"), plan.keySet());
    }

    @Test void theKeyIsThePackedUuidNotTheRealOne() {
        var clone = new Combatant("clone-uuid", UUID.randomUUID());
        var actors = List.of(new Actor(Kind.PLAYER, List.of(clone)));
        var plan = BattleFxPlanner.plan(actors, false, true, List.of(), id -> Optional.of(snapshot("p")), BattleFxPlannerTest::none);
        assertEquals(java.util.Set.of("clone-uuid"), plan.keySet());
    }
}
