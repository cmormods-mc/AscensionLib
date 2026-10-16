package com.cobbleascend.store;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.RankedRules;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How fast the store commits, because every operation runs on the server thread with a durable write (synchronous=FULL). The figures are printed so they can be
 * recorded in docs/PERFORMANCE.md; the assertion is only a loose floor that fails if a commit becomes pathologically slow.
 */
class StoreThroughputTest {
    private static final UUID AUTH = UUID.fromString("9d6bb263-d230-42d7-a9d0-ea4710df4bb5");
    @TempDir Path dir;

    @Test void sequentialCommitsAndReadsStayFast() {
        try (var store = ProgressionStore.open(dir.resolve("progression.db"), RankedRules.defaults(), AUTH)) {
            var player = UUID.randomUUID();
            int writes = 400;
            long start = System.nanoTime();
            for (int i = 0; i < writes; i++) store.grant(UUID.randomUUID(), player, Map.of(MaterialId.RESONANCE_DUST, 1L), "bench");
            double writeMs = (System.nanoTime() - start) / 1e6 / writes;
            start = System.nanoTime();
            int reads = 2000;
            for (int i = 0; i < reads; i++) store.wallet(player);
            double readMs = (System.nanoTime() - start) / 1e6 / reads;
            // a busy server: 20 players each earning at the same moment
            var players = new UUID[20];
            for (int i = 0; i < players.length; i++) players[i] = UUID.randomUUID();
            start = System.nanoTime();
            for (var p : players) store.grant(UUID.randomUUID(), p, Map.of(MaterialId.RESONANCE_DUST, 5L, MaterialId.FACET, 1L), "bench");
            double burstMs = (System.nanoTime() - start) / 1e6;
            System.out.printf("STORE THROUGHPUT: write %.2f ms each, read %.3f ms each, burst of 20 writes %.1f ms%n", writeMs, readMs, burstMs);
            assertEquals(writes, store.wallet(player).balance(MaterialId.RESONANCE_DUST));
            assertTrue(writeMs < 250, "a durable commit took " + writeMs + " ms on average");
            assertTrue(readMs < 25, "a wallet read took " + readMs + " ms on average");
        }
    }
}
