package com.ascensionlib;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether one of our full-screen pages that another mod must not cover is on a player's screen. CobbleTowers opens its own page the moment a floor is
 * won, in the same tick our completion reveal opens, and reopens it whenever it thinks it was closed; it asks {@link #revealShowing} first and leaves the
 * screen alone while this says yes. A contract method: CobbleTowers looks it up by name (see {@code ContractSignaturesTest}).
 *
 * <p>WebGUI tells the server nothing when a page is closed with Esc, so "showing" is a heartbeat the page keeps up, plus a short grace while it loads.
 */
public final class AscensionScreens {
    private AscensionScreens() {}

    /** How long a freshly opened page is taken to be showing before its first heartbeat. */
    static final long LOAD_GRACE_MILLIS = 8_000;
    /** How long one heartbeat counts (the page sends one every 1.5 s). */
    static final long BEAT_MILLIS = 4_000;

    private static final Map<UUID, Long> UNTIL = new ConcurrentHashMap<>();

    /** True while the player's completion reveal is on screen. Safe from any thread. */
    public static boolean revealShowing(UUID player) {
        return revealShowing(player, System.currentTimeMillis());
    }

    static boolean revealShowing(UUID player, long now) {
        Long until = UNTIL.get(player);
        if (until == null) return false;
        if (now < until) return true;
        UNTIL.remove(player, until);
        return false;
    }

    /** The reveal was just opened for the player. */
    public static void revealOpened(UUID player, long now) {
        UNTIL.put(player, now + LOAD_GRACE_MILLIS);
    }

    /** The open page said it is still there. */
    public static void revealBeat(UUID player, long now) {
        UNTIL.merge(player, now + BEAT_MILLIS, Math::max);
    }

    /** The player closed the reveal with its own buttons. */
    public static void revealClosed(UUID player) {
        UNTIL.remove(player);
    }
}
