package com.cobbleascend.store;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.Rarity;
import com.cobbleascend.domain.v1.*;
import com.cobbleascend.domain.v1.CraftException.Reason;
import com.cobbleascend.store.StoreException.Code;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spooky Essence buys a random Haunting for one Pokemon (docs/HALLOWEEN-EVENT-DESIGN.md): the store half. */
class HauntingStoreTest {
    private static final UUID AUTH = UUID.fromString("9d6bb263-d230-42d7-a9d0-ea4710df4bb5");

    @TempDir Path dir;
    private final RankedRules rules = RankedRules.defaults();
    private final RankedProgression progression = new RankedProgression(rules);
    private final List<ProgressionStore> opened = new ArrayList<>();
    private final UUID player = UUID.randomUUID();
    private final UUID pokemon = UUID.randomUUID();

    @AfterEach void close() {
        for (var store : opened) try { store.close(); } catch (RuntimeException ignored) { }
    }

    private Path file() { return dir.resolve("world").resolve("progression.db"); }

    private ProgressionStore open() {
        var store = ProgressionStore.open(file(), rules, AUTH, ProgressionStore.Options.defaults().withRandom(new Random(1)));
        opened.add(store);
        return store;
    }

    /** A store with one profiled Pokemon and {@code essence} Spooky Essence in the player's wallet. */
    private ProgressionStore stocked(long essence) {
        var store = open();
        store.acquire(UUID.randomUUID(), pokemon, () -> progression.create(pokemon, AUTH, Rarity.RARE, Origin.of("wild_capture"), 30,
                List.of("fire", "flying"), new Random(5)));
        if (essence > 0) store.grant(UUID.randomUUID(), player, Map.of(MaterialId.SPOOKY_ESSENCE, essence), "test");
        return store;
    }

    private static Code codeOf(Runnable action) { return assertThrows(StoreException.class, action::run).code(); }

    private static Reason reasonOf(Runnable action) { return assertThrows(CraftException.class, action::run).reason(); }

    @Test void theFirstRollCostsOneEssenceAndRecordsAHaunting() {
        var store = stocked(10);
        var op = UUID.randomUUID();
        var outcome = store.haunt(op, player, pokemon, store.wallet(player).revision());
        assertEquals(9, outcome.wallet().balance(MaterialId.SPOOKY_ESSENCE));
        assertEquals(Map.of(MaterialId.SPOOKY_ESSENCE, 1L), outcome.cost());
        var record = store.haunting(pokemon).orElseThrow();
        assertEquals(1, record.rolls());
        assertEquals(op, record.operationId());
        assertEquals(Hauntings.roll(Hauntings.seed(op, pokemon)).id(), record.hauntingId(), "the result is the seeded draw");
        assertTrue(Hauntings.byId(record.hauntingId()).isPresent());
    }

    @Test void theProfileRevisionAdvancesSoCachesRefresh() {
        var store = stocked(10);
        long before = store.profile(pokemon).orElseThrow().revision();
        var outcome = store.haunt(UUID.randomUUID(), player, pokemon, CraftRequest.ANY_REVISION);
        assertEquals(before + 1, outcome.profile().revision());
        assertEquals(before + 1, store.profile(pokemon).orElseThrow().revision());
    }

    @Test void aRetryIsAReplayThatChangesNothingAndCostsNothingMore() {
        var store = stocked(10);
        var op = UUID.randomUUID();
        long revision = store.wallet(player).revision();
        var first = store.haunt(op, player, pokemon, revision);
        var again = store.haunt(op, player, pokemon, revision);
        assertTrue(again.replayed());
        assertEquals(first.wallet().balance(MaterialId.SPOOKY_ESSENCE), again.wallet().balance(MaterialId.SPOOKY_ESSENCE));
        assertEquals(9, store.wallet(player).balance(MaterialId.SPOOKY_ESSENCE), "charged once");
        assertEquals(1, store.haunting(pokemon).orElseThrow().rolls());
    }

    @Test void rerollsCostMoreAndReplaceTheHaunting() {
        var store = stocked(100);
        long[] expected = {1, 3, 5, 8, 8};
        long spent = 0;
        for (int i = 0; i < expected.length; i++) {
            var outcome = store.haunt(UUID.randomUUID(), player, pokemon, CraftRequest.ANY_REVISION);
            assertEquals(expected[i], outcome.cost().get(MaterialId.SPOOKY_ESSENCE), "roll " + (i + 1));
            spent += expected[i];
            assertEquals(i + 1, store.haunting(pokemon).orElseThrow().rolls());
        }
        assertEquals(100 - spent, store.wallet(player).balance(MaterialId.SPOOKY_ESSENCE));
        assertEquals(1, store.hauntedPokemon().size(), "one row per Pokemon, replaced in place");
    }

    @Test void aShortWalletChangesNothing() {
        var store = stocked(0);
        assertEquals(Reason.INSUFFICIENT_FUNDS, reasonOf(() -> store.haunt(UUID.randomUUID(), player, pokemon, CraftRequest.ANY_REVISION)));
        assertTrue(store.haunting(pokemon).isEmpty());
        store.grant(UUID.randomUUID(), player, Map.of(MaterialId.SPOOKY_ESSENCE, 1L), "test");
        store.haunt(UUID.randomUUID(), player, pokemon, CraftRequest.ANY_REVISION);
        assertEquals(Reason.INSUFFICIENT_FUNDS, reasonOf(() -> store.haunt(UUID.randomUUID(), player, pokemon, CraftRequest.ANY_REVISION)), "the second roll costs 3");
        assertEquals(1, store.haunting(pokemon).orElseThrow().rolls());
        assertEquals(0, store.wallet(player).balance(MaterialId.SPOOKY_ESSENCE));
    }

    @Test void aPokemonWithNoProfileCannotBeHauntedAndNothingIsSpent() {
        var store = open();
        store.grant(UUID.randomUUID(), player, Map.of(MaterialId.SPOOKY_ESSENCE, 5L), "test");
        assertEquals(Code.UNKNOWN_PROFILE, codeOf(() -> store.haunt(UUID.randomUUID(), player, UUID.randomUUID(), CraftRequest.ANY_REVISION)));
        assertEquals(5, store.wallet(player).balance(MaterialId.SPOOKY_ESSENCE));
    }

    @Test void aWalletThatChangedSinceItWasShownIsRefused() {
        var store = stocked(10);
        long stale = store.wallet(player).revision();
        store.grant(UUID.randomUUID(), player, Map.of(MaterialId.SPOOKY_ESSENCE, 1L), "more");
        assertEquals(Code.STALE_WALLET, codeOf(() -> store.haunt(UUID.randomUUID(), player, pokemon, stale)));
        assertTrue(store.haunting(pokemon).isEmpty());
    }

    @Test void theHauntingSurvivesReopeningTheStore() {
        var store = stocked(10);
        var op = UUID.randomUUID();
        store.haunt(op, player, pokemon, CraftRequest.ANY_REVISION);
        var id = store.haunting(pokemon).orElseThrow().hauntingId();
        store.close();
        opened.clear();
        var reopened = open();
        assertEquals(id, reopened.haunting(pokemon).orElseThrow().hauntingId());
        assertEquals(Map.of(pokemon, id), reopened.hauntedPokemon());
    }

    @Test void anOlderStoreGainsTheTableWhenItIsOpened() throws Exception {
        stocked(0).close();
        opened.clear();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file().toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE hauntings");
            statement.executeUpdate("UPDATE meta SET value = '4' WHERE key = 'schema_version'");
        }
        var store = open();
        store.grant(UUID.randomUUID(), player, Map.of(MaterialId.SPOOKY_ESSENCE, 2L), "test");
        store.haunt(UUID.randomUUID(), player, pokemon, CraftRequest.ANY_REVISION);
        assertTrue(store.haunting(pokemon).isPresent(), "the migrated store takes a Haunting");
    }

    @Test void theCommitListenerHearsTheHauntingAsTheDetail() {
        var store = stocked(10);
        var heard = new ArrayList<Committed>();
        store.onCommit(heard::add);
        var op = UUID.randomUUID();
        store.haunt(op, player, pokemon, CraftRequest.ANY_REVISION);
        assertEquals(1, heard.size());
        assertEquals(Kind.HAUNT, heard.get(0).kind());
        assertEquals(Hauntings.roll(Hauntings.seed(op, pokemon)).id(), heard.get(0).detail());
    }
}
