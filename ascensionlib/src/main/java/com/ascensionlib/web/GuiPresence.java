package com.ascensionlib.web;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a player has one of our full-screen pages open. WebGUI shows one page at a time and its {@code openHud} destroys an open GUI page,
 * and a page's {@code close} message closes the GUI before the overlay, so the overlay must neither open nor close while a GUI is up. The server
 * cannot see a client-side screen close, so every GUI page sends a beat every couple of seconds ({@code ascension_gui_beat}); a player counts as
 * having a GUI open until the beats stop.
 */
public final class GuiPresence {
    /** Longer than the page's 2 s beat so one late message does not read as a close, short enough that a closed page frees the overlay quickly. */
    private static final long WINDOW_MILLIS = 5_000;

    private static final Map<UUID, Long> LAST_BEAT = new ConcurrentHashMap<>();

    private GuiPresence() {}

    public static void register() {
        WebUi.listen("ascension_gui_beat", (player, o) -> touch(player.getUUID()));
    }

    /** A GUI page was just opened for this player, or reported in. */
    public static void touch(UUID player) { LAST_BEAT.put(player, System.currentTimeMillis()); }

    public static boolean isOpen(UUID player) {
        Long last = LAST_BEAT.get(player);
        if (last == null) return false;
        if (System.currentTimeMillis() - last < WINDOW_MILLIS) return true;
        LAST_BEAT.remove(player, last);
        return false;
    }

    private static final Map<UUID, Long> LAST_LOG = new ConcurrentHashMap<>();

    /** Says (at most every 10 s per player) that the overlay is waiting on a full-screen page, so a panel that never shows can be explained. */
    public static void logHeld(UUID player) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(player);
        if (last != null && now - last < 10_000) return;
        LAST_LOG.put(player, now);
        org.slf4j.LoggerFactory.getLogger("AscensionLib").info("Overlay held back for {}: a full-screen page is reporting open", player);
    }

    public static void forget(UUID player) { LAST_BEAT.remove(player); LAST_LOG.remove(player); }
}
