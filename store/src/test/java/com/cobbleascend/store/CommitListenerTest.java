package com.cobbleascend.store;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.RankedRules;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The commit listener is how the telemetry log sees the economy, so what it reports must match what the wallet did. */
class CommitListenerTest {
    private static final UUID AUTH = UUID.fromString("9d6bb263-d230-42d7-a9d0-ea4710df4bb5");

    @TempDir Path dir;
    private ProgressionStore store;
    private final List<Committed> seen = new ArrayList<>();
    private final UUID player = UUID.randomUUID();

    private ProgressionStore open() {
        store = ProgressionStore.open(dir.resolve("progression.db"), RankedRules.defaults(), AUTH);
        store.onCommit(seen::add);
        return store;
    }

    @AfterEach void close() { if (store != null) store.close(); }

    @Test void aGrantReportsWhatEnteredTheWalletAndWhy() {
        open().grant(UUID.randomUUID(), player, Map.of(MaterialId.RESONANCE_DUST, 40L), "encounter x boss_defeated");
        assertEquals(1, seen.size());
        var c = seen.get(0);
        assertEquals(Kind.GRANT, c.kind());
        assertEquals(player, c.playerId());
        assertEquals(Map.of(MaterialId.RESONANCE_DUST, 40L), c.gained());
        assertTrue(c.spent().isEmpty());
        assertEquals("encounter x boss_defeated", c.detail());
        assertEquals(40L, c.wallet().balance(MaterialId.RESONANCE_DUST));
        assertTrue(c.nanos() > 0);
    }

    @Test void aSpendAndAnExchangeReportBothSides() {
        var s = open();
        s.grant(UUID.randomUUID(), player, Map.of(MaterialId.TOWER_MARK, 10L), "seed");
        seen.clear();
        s.spend(UUID.randomUUID(), player, Map.of(MaterialId.TOWER_MARK, 2L), "scouter");
        s.exchange(UUID.randomUUID(), player, Map.of(MaterialId.TOWER_MARK, 5L), Map.of(MaterialId.RANK_RESPEC, 1L), "hourglass", CraftRequest.ANY_REVISION);
        assertEquals(Map.of(MaterialId.TOWER_MARK, 2L), seen.get(0).spent());
        assertEquals(Map.of(MaterialId.TOWER_MARK, 5L), seen.get(1).spent());
        assertEquals(Map.of(MaterialId.RANK_RESPEC, 1L), seen.get(1).gained());
    }

    @Test void aReplayAndAFailureAreNotReported() {
        var s = open();
        var op = UUID.randomUUID();
        s.grant(op, player, Map.of(MaterialId.FACET, 3L), "seed");
        s.grant(op, player, Map.of(MaterialId.FACET, 3L), "seed");
        assertEquals(1, seen.size(), "the second call is a replay: nothing changed");
        assertThrows(RuntimeException.class, () -> s.spend(UUID.randomUUID(), player, Map.of(MaterialId.FACET, 99L), "too much"));
        assertEquals(1, seen.size(), "a refused spend changed nothing");
    }

    @Test void aListenerThatThrowsCannotUndoTheCommit() {
        var s = open();
        s.onCommit(c -> { throw new IllegalStateException("log is broken"); });
        s.grant(UUID.randomUUID(), player, Map.of(MaterialId.FACET, 3L), "seed");
        assertEquals(3L, s.wallet(player).balance(MaterialId.FACET));
    }

    @Test void supplyTotalsEveryWallet() {
        var s = open();
        s.grant(UUID.randomUUID(), player, Map.of(MaterialId.RESONANCE_DUST, 40L), "a");
        s.grant(UUID.randomUUID(), UUID.randomUUID(), Map.of(MaterialId.RESONANCE_DUST, 2L, MaterialId.FACET, 1L), "b");
        var supply = s.materialSupply();
        assertEquals(42L, supply.get(MaterialId.RESONANCE_DUST));
        assertEquals(1L, supply.get(MaterialId.FACET));
        assertEquals(2, s.walletCount());
    }
}
