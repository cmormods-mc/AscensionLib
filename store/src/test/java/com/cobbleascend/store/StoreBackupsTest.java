package com.cobbleascend.store;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.RankedRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreBackupsTest {
    private static final UUID AUTH = UUID.fromString("9d6bb263-d230-42d7-a9d0-ea4710df4bb5");
    @TempDir Path dir;
    private final RankedRules rules = RankedRules.defaults();
    private final UUID player = UUID.randomUUID();

    private Path file() { return dir.resolve("progression.db"); }


    @Test void aSnapshotOfTheLiveStoreHoldsEveryCommittedOperationAndOpensOnItsOwn() throws Exception {
        try (var store = ProgressionStore.open(file(), rules, AUTH)) {
            store.grant(UUID.randomUUID(), player, Map.of(MaterialId.RESONANCE_DUST, 321L), "test");
            var copy = dir.resolve("copies/one.db");
            store.backupTo(copy);
            store.grant(UUID.randomUUID(), player, Map.of(MaterialId.RESONANCE_DUST, 100L), "after");   // the live store keeps working
            assertEquals(421, store.wallet(player).balance(MaterialId.RESONANCE_DUST));
            assertTrue(Files.size(copy) > 0);
            try (var reopened = ProgressionStore.open(copy, rules, AUTH)) {
                assertEquals(321, reopened.wallet(player).balance(MaterialId.RESONANCE_DUST), "the copy is the store as it was, and it opens as a store");
            }
            assertThrows(StoreException.class, () -> store.backupTo(copy), "an existing file is never overwritten");
        }
    }

    @Test void upgradingAnOlderStoreCopiesItFirstAndKeepsTheCopyAtTheOldSchema() throws Exception {
        try (var store = ProgressionStore.open(file(), rules, AUTH)) {
            store.grant(UUID.randomUUID(), player, Map.of(MaterialId.RESONANCE_DUST, 77L), "test");
        }
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + file().toAbsolutePath()); var s = c.createStatement()) {
            s.executeUpdate("UPDATE meta SET value = '3' WHERE key = 'schema_version'");
        }
        try (var upgraded = ProgressionStore.open(file(), rules, AUTH)) {
            assertEquals(77, upgraded.wallet(player).balance(MaterialId.RESONANCE_DUST));
        }
        try (var files = Files.list(dir)) {
            var backups = files.filter(p -> p.getFileName().toString().contains(".before-schema5-from3-")).toList();
            assertEquals(1, backups.size(), "one copy, named for the schema it came from");
            try (var c = DriverManager.getConnection("jdbc:sqlite:" + backups.get(0).toAbsolutePath()); var s = c.createStatement();
                 var rows = s.executeQuery("SELECT value FROM meta WHERE key = 'schema_version'")) {
                assertTrue(rows.next());
                assertEquals("3", rows.getString(1), "the copy is the old schema, untouched by the upgrade");
            }
        }
    }

    @Test void aStoreAlreadyCurrentOrBrandNewIsNotCopied() throws Exception {
        try (var store = ProgressionStore.open(file(), rules, AUTH)) { store.grant(UUID.randomUUID(), player, Map.of(MaterialId.FACET, 1L), "t"); }
        try (var again = ProgressionStore.open(file(), rules, AUTH)) { assertNotNull(again); }
        try (var files = Files.list(dir)) {
            assertEquals(0, files.filter(p -> p.getFileName().toString().endsWith(".bak")).count());
        }
    }

    @Test void snapshotsAreDatedDueDailyAndOnlyTheNewestFewAreKept() throws Exception {
        var folder = dir.resolve("backups");
        var start = Instant.parse("2026-10-01T10:00:00Z");
        try (var store = ProgressionStore.open(file(), rules, AUTH)) {
            assertTrue(StoreBackups.due(folder, Clock.fixed(start, ZoneOffset.UTC)), "none yet: due");
            Path first = StoreBackups.backup(store, folder, Clock.fixed(start, ZoneOffset.UTC));
            assertEquals("progression-20261001-100000.db", first.getFileName().toString());
            assertEquals(first, StoreBackups.backup(store, folder, Clock.fixed(start, ZoneOffset.UTC)), "the same second is the same file");
            Files.setLastModifiedTime(first, java.nio.file.attribute.FileTime.from(start));
            assertFalse(StoreBackups.due(folder, Clock.fixed(start.plus(Duration.ofHours(23)), ZoneOffset.UTC)));
            assertTrue(StoreBackups.due(folder, Clock.fixed(start.plus(Duration.ofHours(25)), ZoneOffset.UTC)));
            assertTrue(StoreBackups.backupIfDue(store, folder, Clock.fixed(start.plus(Duration.ofHours(1)), ZoneOffset.UTC)).isEmpty());
            for (int day = 1; day <= 20; day++) StoreBackups.backup(store, folder, Clock.fixed(start.plus(Duration.ofDays(day)), ZoneOffset.UTC));
            var kept = StoreBackups.list(folder);
            assertEquals(StoreBackups.KEEP, kept.size(), "only the newest are kept");
            assertTrue(kept.get(0).getFileName().toString().compareTo("progression-20261007") > 0, "the oldest were removed");
            assertEquals("progression-20261021-100000.db", kept.get(kept.size() - 1).getFileName().toString());
        }
    }
}
