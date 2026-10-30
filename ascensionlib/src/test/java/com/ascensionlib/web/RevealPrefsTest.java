package com.ascensionlib.web;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RevealPrefsTest {
    @TempDir Path dir;

    @Test void revealsDefaultToEnabledAndDisablingPersistsOnlyForThatPlayer() {
        var file = dir.resolve("reveals.json");
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        var prefs = new RevealPrefs(file);
        assertTrue(prefs.enabled(first)); prefs.setEnabled(first, false);
        var reopened = new RevealPrefs(file);
        assertFalse(reopened.enabled(first)); assertTrue(reopened.enabled(second));
        reopened.setEnabled(first, true); assertTrue(new RevealPrefs(file).enabled(first));
    }
}
