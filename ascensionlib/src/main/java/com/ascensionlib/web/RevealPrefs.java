package com.ascensionlib.web;

import java.nio.file.Path;
import java.util.UUID;

/** Reveal opt-outs use the existing durable UUID-set format, in their own world file. */
final class RevealPrefs {
    private final CalmPrefs disabled;

    RevealPrefs(Path file) { disabled = new CalmPrefs(file); }
    boolean enabled(UUID player) { return !disabled.get(player); }
    void setEnabled(UUID player, boolean enabled) { disabled.set(player, !enabled); }
}
