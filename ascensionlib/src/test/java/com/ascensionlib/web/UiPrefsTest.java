package com.ascensionlib.web;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiPrefsTest {
    @TempDir Path dir;

    @Test void everyoneStartsAtOneHundredPercent() {
        assertEquals(100, new UiPrefs(dir.resolve("ui.json")).get(UUID.randomUUID()));
    }

    @Test void aChoiceIsClampedToTheRangeAndKeptAcrossARestart() {
        var file = dir.resolve("world/ui.json");
        var player = UUID.randomUUID();
        var prefs = new UiPrefs(file);
        assertEquals(140, prefs.set(player, 140));
        assertEquals(160, prefs.set(UUID.randomUUID(), 5000), "above the range is clamped");
        var other = UUID.randomUUID();
        assertEquals(60, prefs.set(other, 1), "below the range is clamped");
        var reopened = new UiPrefs(file);
        assertEquals(140, reopened.get(player));
        assertEquals(60, reopened.get(other));
    }

    @Test void theDefaultIsNotStoredAndPlayersAreIndependent() throws Exception {
        var file = dir.resolve("ui.json");
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var prefs = new UiPrefs(file);
        prefs.set(a, 130);
        prefs.set(b, 80);
        prefs.set(a, 100);
        assertEquals(100, prefs.get(a));
        assertEquals(80, prefs.get(b));
        assertFalse(Files.readString(file).contains(a.toString()), "a player back at 100 leaves no entry");
    }

    @Test void aCorruptOrMissingFileMeansTheDefaultAndNothingBreaks() throws Exception {
        var file = dir.resolve("ui.json");
        Files.writeString(file, "{ this is not json");
        var prefs = new UiPrefs(file);
        assertEquals(100, prefs.get(UUID.randomUUID()));
        var player = UUID.randomUUID();
        prefs.set(player, 120);
        assertEquals(120, new UiPrefs(file).get(player), "and saving works again");
    }

    @Test void oneBadEntryIsSkippedWithoutLosingTheOthers() throws Exception {
        var file = dir.resolve("ui.json");
        var good = UUID.randomUUID();
        Files.writeString(file, "{\"not-a-uuid\": 120, \"" + good + "\": 150, \"" + UUID.randomUUID() + "\": \"wide\"}");
        assertEquals(150, new UiPrefs(file).get(good));
    }
}
