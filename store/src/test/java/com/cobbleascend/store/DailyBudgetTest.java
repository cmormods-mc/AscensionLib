package com.cobbleascend.store;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.DailyRewardBudget;
import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.RankedRules;
import com.cobbleascend.store.EncounterRewards.EncounterOutcome;
import com.cobbleascend.store.EncounterRewards.Payout;
import com.cobbleascend.store.EncounterRewards.Settlement;
import com.cobbleascend.store.EncounterRewards.Status;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DailyBudgetTest {
    private static final UUID AUTH = UUID.fromString("9d6bb263-d230-42d7-a9d0-ea4710df4bb5");
    private static final DailyRewardBudget THREE = new DailyRewardBudget(3, 25);

    /** A clock the test moves by hand. */
    private static final class Hand extends Clock {
        long millis = Instant.parse("2026-10-15T10:00:00Z").toEpochMilli();
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }

    @TempDir Path dir;
    private final Hand clock = new Hand();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private ProgressionStore store;
    private EncounterRewards rewards;

    @BeforeEach void open() {
        var options = new ProgressionStore.Options(ProgressionStore.OpenMode.CREATE_IF_ABSENT, StoreConfig.defaults(), clock, new SecureRandom(),
                Faults.NONE);
        store = ProgressionStore.open(dir.resolve("progression.db"), RankedRules.defaults(), AUTH, options);
        rewards = new EncounterRewards(store);
    }

    @AfterEach void close() { store.close(); }

    private Map<UUID, EncounterRewards.Budgeted> chest(String encounter, UUID player, long dust, long cores) {
        var materials = cores > 0 ? Map.of(MaterialId.RESONANCE_DUST, dust, MaterialId.ASCENSION_CORE, cores) : Map.of(MaterialId.RESONANCE_DUST, dust);
        return rewards.settleBudgeted(new Settlement(encounter, EncounterOutcome.VICTORY, "tower_completion", List.of(new Payout(player, materials))), THREE);
    }

    private long dust(UUID player) { return store.wallet(player).balance(MaterialId.RESONANCE_DUST); }

    @Test void theFirstChestsOfTheDayPayInFullAndTheNextOnesPayAQuarterOfTheDust() {
        for (int i = 0; i < 3; i++) {
            var answer = chest("e" + i, alice, 1000, 1).get(alice);
            assertEquals(Status.GRANTED, answer.status());
            assertFalse(answer.reduced());
        }
        assertEquals(3000, dust(alice));
        var fourth = chest("e3", alice, 1000, 1).get(alice);
        assertTrue(fourth.reduced());
        assertEquals(3250, dust(alice), "a quarter of the dust");
        assertEquals(4, store.wallet(alice).balance(MaterialId.ASCENSION_CORE), "the Core is never cut");
        assertEquals(4, store.budgetUsedToday(alice));
    }

    @Test void aRetryReplaysTheFirstPaymentAndUsesNoBudget() {
        chest("e0", alice, 1000, 0);
        chest("e1", alice, 1000, 0);
        chest("e2", alice, 1000, 0);
        var reduced = chest("e3", alice, 1000, 0).get(alice);
        assertTrue(reduced.reduced());
        long after = dust(alice);
        var again = chest("e3", alice, 1000, 0).get(alice);
        assertEquals(Status.ALREADY_GRANTED, again.status());
        assertTrue(again.reduced(), "the replay reports what was actually paid");
        assertEquals(after, dust(alice));
        assertEquals(4, store.budgetUsedToday(alice));
        // and an early chest retried after the budget ran out still reports the full payment it got
        assertFalse(chest("e0", alice, 1000, 0).get(alice).reduced());
    }

    @Test void theBudgetIsPerPlayerAndResetsAtMidnightUtc() {
        for (int i = 0; i < 4; i++) chest("a" + i, alice, 1000, 0);
        assertTrue(chest("a4", alice, 1000, 0).get(alice).reduced());
        assertFalse(chest("b0", bob, 1000, 0).get(bob).reduced(), "another player has their own budget");
        clock.millis = Instant.parse("2026-10-16T00:00:01Z").toEpochMilli();
        assertEquals(0, store.budgetUsedToday(alice));
        assertFalse(chest("a5", alice, 1000, 0).get(alice).reduced(), "a new day pays in full again");
    }

    @Test void aDefeatPaysNothingAndUsesNoBudget() {
        var answer = rewards.settleBudgeted(new Settlement("lost", EncounterOutcome.DEFEAT, "tower_completion",
                List.of(new Payout(alice, Map.of(MaterialId.RESONANCE_DUST, 500L)))), THREE).get(alice);
        assertEquals(Status.NOT_PAID, answer.status());
        assertEquals(0, store.budgetUsedToday(alice));
    }

    @Test void aChestWithNoDustLosesNothingWhenReduced() {
        for (int i = 0; i < 3; i++) chest("e" + i, alice, 10, 0);
        var coresOnly = rewards.settleBudgeted(new Settlement("c", EncounterOutcome.VICTORY, "tower_boss",
                List.of(new Payout(alice, Map.of(MaterialId.ASCENSION_CORE, 2L)))), THREE).get(alice);
        assertFalse(coresOnly.reduced());
        assertEquals(2, store.wallet(alice).balance(MaterialId.ASCENSION_CORE));
    }

    @Test void theReducedShareRoundsAndNeverRemovesAllTheDust() {
        var budget = new DailyRewardBudget(0, 25);
        assertEquals(1L, budget.reduced(Map.of(MaterialId.RESONANCE_DUST, 2L)).get(MaterialId.RESONANCE_DUST), "at least one when any was due");
        assertEquals(250L, budget.reduced(Map.of(MaterialId.RESONANCE_DUST, 1000L)).get(MaterialId.RESONANCE_DUST));
        assertFalse(new DailyRewardBudget(0, 0).reduced(Map.of(MaterialId.RESONANCE_DUST, 50L)).containsKey(MaterialId.RESONANCE_DUST));
        assertThrows(IllegalArgumentException.class, () -> new DailyRewardBudget(5, 101));
        assertThrows(IllegalArgumentException.class, () -> new DailyRewardBudget(-1, 25));
    }
}
