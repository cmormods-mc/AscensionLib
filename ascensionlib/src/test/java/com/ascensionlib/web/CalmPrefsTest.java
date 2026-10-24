package com.ascensionlib.web;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CalmPrefsTest {
    @TempDir Path dir;

    @Test void everyoneStartsWithEffectsOn() {
        assertFalse(new CalmPrefs(dir.resolve("calm.json")).get(UUID.randomUUID()));
    }

    @Test void aChoiceIsKeptAcrossARestartAndPlayersAreIndependent() {
        var file = dir.resolve("world/calm.json");
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var prefs = new CalmPrefs(file);
        assertTrue(prefs.set(a, true));
        prefs.set(b, true);
        prefs.set(b, false);
        var reopened = new CalmPrefs(file);
        assertTrue(reopened.get(a));
        assertFalse(reopened.get(b));
    }

    @Test void aCorruptFileMeansEffectsOnAndSavingWorksAgain() throws Exception {
        var file = dir.resolve("calm.json");
        Files.writeString(file, "{ this is not json");
        var prefs = new CalmPrefs(file);
        var player = UUID.randomUUID();
        assertFalse(prefs.get(player));
        prefs.set(player, true);
        assertTrue(new CalmPrefs(file).get(player));
    }

    @Test void oneBadEntryIsSkippedWithoutLosingTheOthers() throws Exception {
        var file = dir.resolve("calm.json");
        var good = UUID.randomUUID();
        Files.writeString(file, "[\"not-a-uuid\", \"" + good + "\"]");
        assertTrue(new CalmPrefs(file).get(good));
    }
}
