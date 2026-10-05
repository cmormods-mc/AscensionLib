package com.cobbleascend.store;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.*;
import com.cobbleascend.domain.v1.CraftException.Reason;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScoutingServiceTest {
    private static final UUID AUTH = UUID.fromString("9d6bb263-d230-42d7-a9d0-ea4710df4bb5");

    @TempDir Path dir;
    private final RankedRules rules = RankedRules.defaults();
    private final UUID player = UUID.randomUUID();
    private ProgressionStore store;
    private ScoutingService scouting;

    @BeforeEach void open() {
        store = ProgressionStore.open(dir.resolve("progression.db"), rules, AUTH);
        scouting = new ScoutingService(store);
    }

    @AfterEach void close() { store.close(); }

    private CombatSnapshot enemy(String encounter, int index) {
        var tier = EnemyTiers.defaults().tier("trial_rank_2");
        return new EnemyGenerator(rules).generate(encounter, index, EnemySpec.regular(tier), List.of("water", "dark"));
    }

    private long scouters() { return store.wallet(player).balance(MaterialId.SCOUTER); }

    @Test void aTowerRewardPaysOutOnceEvenIfRetried() {
        UUID reward = UUID.randomUUID();
        scouting.grantScouters(reward, player, 2, "tower floor 5");
        scouting.grantScouters(reward, player, 2, "tower floor 5");
        assertEquals(2, scouters());
    }

    @Test void oneUseRevealsOneEnemyAndSpendsExactlyOne() {
        scouting.grantScouters(UUID.randomUUID(), player, 3, "reward");
        var first = enemy("tower-1", 0);
        var result = scouting.use(player, "tower-1", first);
        assertEquals(ScoutingService.Status.REVEALED, result.status());
        assertEquals(first, result.snapshot());
        assertEquals(2, scouters());
        assertTrue(scouting.isRevealed(player, "tower-1", first.subjectId()));
        assertFalse(scouting.isRevealed(player, "tower-1", enemy("tower-1", 1).subjectId()), "Other enemies stay hidden");
    }

    @Test void anAlreadyRevealedEnemyCostsNothing() {
        scouting.grantScouters(UUID.randomUUID(), player, 2, "reward");
        var target = enemy("tower-1", 0);
        scouting.use(player, "tower-1", target);
        assertEquals(ScoutingService.Status.ALREADY_SCOUTED, scouting.use(player, "tower-1", target).status());
        assertEquals(1, scouters());
    }

    @Test void withoutAScouterNothingIsRevealedOrSpent() {
        var result = scouting.use(player, "tower-1", enemy("tower-1", 0));
        assertEquals(ScoutingService.Status.NO_SCOUTER, result.status());
        assertNull(result.snapshot());
        assertFalse(scouting.isRevealed(player, "tower-1", enemy("tower-1", 0).subjectId()));
        assertEquals(0, scouters());
    }

    @Test void aRevealBelongsToOnePlayerAndOneEncounter() {
        var other = UUID.randomUUID();
        scouting.grantScouters(UUID.randomUUID(), player, 1, "reward");
        scouting.grantScouters(UUID.randomUUID(), other, 1, "reward");
        var target = enemy("tower-1", 0);
        scouting.use(player, "tower-1", target);
        assertFalse(scouting.isRevealed(other, "tower-1", target.subjectId()), "Not shared with other players");
        assertEquals(ScoutingService.Status.REVEALED, scouting.use(other, "tower-1", target).status());
        assertEquals(0, store.wallet(other).balance(MaterialId.SCOUTER));
    }

    @Test void revealsEndWithTheEncounterAndAreNeverStored() {
        scouting.grantScouters(UUID.randomUUID(), player, 2, "reward");
        var target = enemy("tower-1", 0);
        scouting.use(player, "tower-1", target);
        scouting.endEncounter("tower-1");
        assertFalse(scouting.isRevealed(player, "tower-1", target.subjectId()));
        // A restart forgets in-memory reveals; only the spend is durable.
        scouting.use(player, "tower-2", enemy("tower-2", 0));
        assertEquals(0, scouters());
        store.close();
        open();
        assertFalse(scouting.isRevealed(player, "tower-2", enemy("tower-2", 0).subjectId()));
        assertEquals(0, scouters());
    }

    @Test void aPlayerCombatantCannotBeScouted() {
        scouting.grantScouters(UUID.randomUUID(), player, 1, "reward");
        var own = CombatSnapshot.ofProfile(new RankedProgression(rules).create(UUID.randomUUID(), AUTH,
                com.cobbleascend.domain.Rarity.RARE, Origin.of("wild_capture"), 20, List.of("fire"), new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> scouting.use(player, "tower-1", own));
        assertEquals(1, scouters());
    }

    @Test void spendRefusesOverdraftsAndIsIdempotent() {
        store.grant(UUID.randomUUID(), player, Map.of(MaterialId.SCOUTER, 1L), "reward");
        UUID op = UUID.randomUUID();
        assertEquals(Reason.INSUFFICIENT_FUNDS, assertThrows(CraftException.class,
                () -> store.spend(UUID.randomUUID(), player, Map.of(MaterialId.SCOUTER, 2L), "too much")).reason());
        assertEquals(1, scouters());
        store.spend(op, player, Map.of(MaterialId.SCOUTER, 1L), "use");
        assertTrue(store.spend(op, player, Map.of(MaterialId.SCOUTER, 1L), "use").replayed());
        assertEquals(0, scouters());
        assertEquals(StoreException.Code.OPERATION_REUSED, assertThrows(StoreException.class,
                () -> store.spend(op, player, Map.of(MaterialId.SCOUTER, 1L), "different reason")).code());
    }

    @Test void aRaidRevealIsSharedButPaidForOnlyByTheUser() {
        var mate = UUID.randomUUID();
        scouting.grantScouters(UUID.randomUUID(), player, 2, "reward");
        var boss = enemy("raid-1", 0);
        assertEquals(ScoutingService.Status.REVEALED, scouting.use(player, "raid-1", boss, ScoutingService.Scope.SHARED).status());
        assertTrue(scouting.isRevealed(mate, "raid-1", boss.subjectId()), "Teammates see it");
        assertEquals(ScoutingService.Status.ALREADY_SCOUTED, scouting.use(mate, "raid-1", boss, ScoutingService.Scope.SHARED).status());
        assertEquals(1, scouters());
        assertEquals(0, store.wallet(mate).balance(MaterialId.SCOUTER));
        assertFalse(scouting.isRevealed(mate, "raid-2", boss.subjectId()), "Only that encounter");
        scouting.endEncounter("raid-1");
        assertFalse(scouting.isRevealed(mate, "raid-1", boss.subjectId()));
    }

    @Test void encounterIdsCannotCollideThroughTheSeparator() {
        scouting.grantScouters(UUID.randomUUID(), player, 1, "reward");
        assertThrows(IllegalArgumentException.class, () -> scouting.use(player, "a|b", enemy("x", 0)));
        assertThrows(IllegalArgumentException.class, () -> scouting.use(player, " ", enemy("x", 0)));
    }
}
