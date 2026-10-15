package com.ascensionlib.web;

import com.ascensionlib.AscensionApi;
import com.ascensionlib.scout.CaptureRevealPayload;
import com.ascensionlib.scout.InspectWeb;
import com.ascensionlib.scout.ScoutPayloads;
import com.cobblemon.mod.common.Cobblemon;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * The overlay ({@code hud.html}): capture cards that appear for a few seconds, and, while the player is in a scouted encounter, a panel
 * of its enemies with the Scouter and a list of their party. It is a WebGUI HUD, so the player keeps playing and presses WebGUI's
 * interact key to click. The server opens it only when there is something to show and the page closes itself when the last thing has
 * been shown. Nothing here changes game state: a click only asks the server to scout (the Scouter rules decide) or to open a screen.
 */
public final class HudWeb {
    /** A reveal waiting for the page to load; the rest are shown as they arrive. At most this many are held. */
    private static final int MAX_QUEUE = 8;

    /** Players whose overlay has loaded and can take events, and those asked to open it that have not reported in yet. */
    private static final Set<UUID> OPEN = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> OPENING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Deque<CaptureRevealPayload>> QUEUED = new ConcurrentHashMap<>();

    private HudWeb() {}

    public static void register() {
        WebUi.listen("ascension_hud_ready", (player, o) -> {
            OPEN.add(player.getUUID());
            OPENING.remove(player.getUUID());
            var queued = QUEUED.remove(player.getUUID());
            if (queued != null) queued.forEach(reveal -> WebUi.emit(player, "hudReveal", reveal));
            var state = com.ascensionlib.scout.ScoutEncounters.stateFor(player.getUUID());
            WebUi.emit(player, "hudScout", Views.state(state));
            if (!state.entries().isEmpty()) WebUi.emit(player, "hudTeam", team(player));
        });
        WebUi.listen("ascension_hud_closed", (player, o) -> { OPEN.remove(player.getUUID()); OPENING.remove(player.getUUID()); });
    }

    /** Forgets what is held for a player who left. */
    public static void forget(UUID player) {
        OPEN.remove(player);
        OPENING.remove(player);
        QUEUED.remove(player);
    }

    private static void ensureOpen(ServerPlayer player) {
        if (!OPEN.contains(player.getUUID()) && !OPENING.contains(player.getUUID()) && WebUi.openHud(player, "hud.html")) OPENING.add(player.getUUID());
    }

    /** Shows a capture card. False when the player's client cannot show web pages (the chat line stays the only notice). */
    public static boolean reveal(ServerPlayer player, CaptureRevealPayload payload) {
        if (!WebUi.canShow(player)) return false;
        if (OPEN.contains(player.getUUID())) {
            WebUi.emit(player, "hudReveal", payload);
            return true;
        }
        var queue = QUEUED.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (queue.size() < MAX_QUEUE) queue.add(payload);
        ensureOpen(player);
        return true;
    }

    /** The scouting state changed for this player: refresh the panel, opening the overlay when an encounter begins. */
    public static void pushScout(ServerPlayer player, ScoutPayloads.State state) {
        InspectWeb.onScout(player, state);
        if (!WebUi.canShow(player)) return;
        if (state.entries().isEmpty() && !OPEN.contains(player.getUUID())) return;
        if (!state.entries().isEmpty()) ensureOpen(player);
        if (OPEN.contains(player.getUUID())) {
            WebUi.emit(player, "hudScout", Views.state(state));
            if (!state.entries().isEmpty()) WebUi.emit(player, "hudTeam", team(player));
        }
    }

    private static List<Views.TeamView> team(ServerPlayer player) {
        var service = AscensionApi.service().orElse(null);
        var out = new ArrayList<Views.TeamView>();
        for (var pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon.isBattleClone()) continue;
            String rarity = service == null ? "" : service.canonical(pokemon).map(p -> p.rarity().id()).orElse("");
            String name = pokemon.getDisplayName(false).getString();
            out.add(new Views.TeamView(pokemon.getUuid().toString(), name.length() > 60 ? name.substring(0, 60) : name,
                    pokemon.getSpecies().getResourceIdentifier().toString(), rarity, pokemon.getLevel()));
        }
        return out;
    }
}
