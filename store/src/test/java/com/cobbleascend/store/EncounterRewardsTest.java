package com.cobbleascend.store;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.RankedRules;
import com.cobbleascend.store.EncounterRewards.EncounterOutcome;
import com.cobbleascend.store.EncounterRewards.Payout;
import com.cobbleascend.store.EncounterRewards.Settlement;
import com.cobbleascend.store.EncounterRewards.Status;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncounterRewardsTest {
    private static final UUID AUTH = UUID.fromString("9d6bb263-d230-42d7-a9d0-ea4710df4bb5");

    @TempDir Path dir;
    private final RankedRules rules = RankedRules.defaults();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private ProgressionStore store;
    private EncounterRewards rewards;

    @BeforeEach void open() {
        store = ProgressionStore.open(dir.resolve("progression.db"), rules, AUTH);
        rewards = new EncounterRewards(store);
    }

    @AfterEach void close() { store.close(); }

    private long balance(UUID player) { return store.wallet(player).balance(MaterialId.RESONANCE_DUST); }

    private static Payout dust(UUID player, long amount) { return new Payout(player, Map.of(MaterialId.RESONANCE_DUST, amount)); }

    private static Settlement victory(String encounter, String kind, Payout... payouts) {
        return new Settlement(encounter, EncounterOutcome.VICTORY, kind, List.of(payouts));
    }

    @Test void aVictoryPaysEachParticipantOnce() {
        var result = rewards.settle(victory("e1", "boss_defeated", dust(alice, 10), dust(bob, 4)));
        assertEquals(Map.of(alice, Status.GRANTED, bob, Status.GRANTED), result);
        assertEquals(10, balance(alice));
        assertEquals(4, balance(bob));
    }

    @Test void aRepeatedCallbackNeitherLosesNorDuplicates() {
        rewards.settle(victory("e1", "boss_defeated", dust(alice, 10)));
        var again = rewards.settle(victory("e1", "boss_defeated", dust(alice, 10)));
        assertEquals(Status.ALREADY_GRANTED, again.get(alice));
        assertEquals(10, balance(alice));
    }

    @Test void aRetryAfterACrashPaysOnlyThoseNotYetPaid() {
        rewards.settle(victory("e1", "boss_defeated", dust(alice, 10)));   // bob's payout never reached the store
        var retry = rewards.settle(victory("e1", "boss_defeated", dust(alice, 10), dust(bob, 4)));
        assertEquals(Map.of(alice, Status.ALREADY_GRANTED, bob, Status.GRANTED), retry);
        assertEquals(10, balance(alice));
        assertEquals(4, balance(bob));
    }

    @Test void theOnceSurvivesARestart() {
        rewards.settle(victory("e1", "boss_defeated", dust(alice, 10)));
        store.close();
        store = ProgressionStore.open(dir.resolve("progression.db"), rules, AUTH);
        rewards = new EncounterRewards(store);
        assertEquals(Status.ALREADY_GRANTED, rewards.settle(victory("e1", "boss_defeated", dust(alice, 10))).get(alice));
        assertEquals(10, balance(alice));
    }

    @Test void defeatAndAbortNeverPay() {
        for (var outcome : List.of(EncounterOutcome.DEFEAT, EncounterOutcome.ABORTED)) {
            var result = rewards.settle(new Settlement("e-" + outcome, outcome, "boss_defeated", List.of(dust(alice, 10))));
            assertEquals(Status.NOT_PAID, result.get(alice));
        }
        assertEquals(0, balance(alice));
        // Nothing was recorded for the losses, so a victory reported under that id is still honoured.
        assertEquals(Status.GRANTED, rewards.settle(victory("e-DEFEAT", "boss_defeated", dust(alice, 10))).get(alice));
    }

    @Test void differentKindsOfOneEncounterEachPayOnce() {
        rewards.settle(victory("e1", "boss_defeated", dust(alice, 10)));
        assertEquals(Status.GRANTED, rewards.settle(victory("e1", "floor_cleared", dust(alice, 3))).get(alice));
        assertEquals(13, balance(alice));
    }

    @Test void theSameKeyWithDifferentAmountsKeepsTheFirstAndReportsAConflict() {
        rewards.settle(victory("e1", "boss_defeated", dust(alice, 10)));
        assertEquals(Status.CONFLICT, rewards.settle(victory("e1", "boss_defeated", dust(alice, 99))).get(alice));
        assertEquals(10, balance(alice));
    }

    @Test void aWalletOverflowRefusesThatPlayerOnly() {
        rewards.settle(victory("e0", "x", dust(alice, Long.MAX_VALUE)));
        var result = rewards.settle(victory("e1", "boss_defeated", dust(alice, 1), dust(bob, 4)));
        assertEquals(Status.REFUSED, result.get(alice));
        assertEquals(Status.GRANTED, result.get(bob));
    }

    @Test void malformedSettlementsAreRejectedBeforeAnythingIsWritten() {
        assertThrows(IllegalArgumentException.class, () -> victory("", "boss_defeated", dust(alice, 1)));
        assertThrows(IllegalArgumentException.class, () -> victory("a|b", "boss_defeated", dust(alice, 1)));
        assertThrows(IllegalArgumentException.class, () -> victory("e1", "Boss Defeated", dust(alice, 1)));
        assertThrows(IllegalArgumentException.class, () -> victory("e1", "boss_defeated", dust(alice, 1), dust(alice, 2)));
        assertThrows(IllegalArgumentException.class, () -> dust(alice, 0));
        assertThrows(IllegalArgumentException.class, () -> new Payout(alice, Map.of()));
        assertEquals(0, balance(alice));
    }
}
