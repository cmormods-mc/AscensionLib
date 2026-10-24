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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
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
    /** The bleeds last pushed to each player, re-sent when the overlay finishes loading. */
    private static final Map<UUID, List<Views.BleedView>> BLEEDS = new ConcurrentHashMap<>();
    /** The Momentum stacks last pushed to each player, re-sent the same way. */
    private static final Map<UUID, List<Views.MomentumView>> MOMENTUM = new ConcurrentHashMap<>();

    /**
     * Players whose overlay work had to wait because a full-screen page was open (see {@link GuiPresence}): WebGUI's openHud destroys an open GUI
     * page, and the overlay's own close would close it too. {@link #flush} picks the work up once the page is gone.
     */
    private static final Set<UUID> DEFERRED = ConcurrentHashMap.newKeySet();

    private HudWeb() {}

    public static void register() {
        WebUi.listen("ascension_hud_ready", (player, o) -> {
            OPEN.add(player.getUUID());
            OPENING.remove(player.getUUID());
            resync(player);
        });
        WebUi.listen("ascension_hud_closed", (player, o) -> { OPEN.remove(player.getUUID()); OPENING.remove(player.getUUID()); });
        // The overlay asks before it closes itself; the answer is no while a full-screen page is up, because the client's close would close that page.
        WebUi.listen("ascension_hud_idle", (player, o) -> {
            if (GuiPresence.isOpen(player.getUUID())) DEFERRED.add(player.getUUID());
            else WebUi.emit(player, "hudClose", Map.of());
        });
        GuiPresence.register();
        ServerTickEvents.END_SERVER_TICK.register(HudWeb::flush);
    }

    /** Sends the overlay everything held for this player: waiting reveals, the bleed and Momentum strips, the scouting panel and the team. */
    private static void resync(ServerPlayer player) {
        var queued = QUEUED.remove(player.getUUID());
        if (queued != null) queued.forEach(reveal -> { WebSprites.feed(player, List.of(reveal.speciesId())); WebUi.emit(player, "hudReveal", reveal); });
        var bleeds = BLEEDS.get(player.getUUID());
        if (bleeds != null && !bleeds.isEmpty()) WebUi.emit(player, "hudBleed", bleeds);
        var momentum = MOMENTUM.get(player.getUUID());
        if (momentum != null && !momentum.isEmpty()) WebUi.emit(player, "hudEffects", new Views.EffectsView(momentum, List.of()));
        var state = com.ascensionlib.scout.ScoutEncounters.stateFor(player.getUUID());
        WebUi.emit(player, "hudScout", Views.state(state));
        if (!state.entries().isEmpty()) sendTeam(player);
    }

    /** Twice a second: gives the overlay back to players whose full-screen page has closed, by opening it or re-sending what it missed. */
    private static void flush(MinecraftServer server) {
        if (DEFERRED.isEmpty() || server.getTickCount() % 10 != 0) return;
        for (UUID id : DEFERRED) {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) { DEFERRED.remove(id); continue; }
            if (GuiPresence.isOpen(id)) continue;
            DEFERRED.remove(id);
            if (OPEN.contains(id)) resync(player); else ensureOpen(player);
        }
    }

    /** Forgets what is held for a player who left. */
    public static void forget(UUID player) {
        OPEN.remove(player);
        OPENING.remove(player);
        QUEUED.remove(player);
        DEFERRED.remove(player);
        GuiPresence.forget(player);
        BLEEDS.remove(player);
        MOMENTUM.remove(player);
        com.ascensionlib.battle.BleedWatch.forget(player);
        com.ascensionlib.battle.Participation.forget(player);
    }

    private static void ensureOpen(ServerPlayer player) {
        if (GuiPresence.isOpen(player.getUUID())) { DEFERRED.add(player.getUUID()); GuiPresence.logHeld(player.getUUID()); return; }
        if (!OPEN.contains(player.getUUID()) && !OPENING.contains(player.getUUID()) && WebUi.openHud(player, "hud.html")) OPENING.add(player.getUUID());
    }

    /** Shows a capture card. False when the player's client cannot show web pages (the chat line stays the only notice). */
    public static boolean reveal(ServerPlayer player, CaptureRevealPayload payload) {
        if (!WebUi.canShow(player)) return false;
        if (OPEN.contains(player.getUUID()) && !GuiPresence.isOpen(player.getUUID())) {
            WebSprites.feed(player, List.of(payload.speciesId()));
            WebUi.emit(player, "hudReveal", payload);
            return true;
        }
        var queue = QUEUED.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        if (queue.size() < MAX_QUEUE) queue.add(payload);
        if (OPEN.contains(player.getUUID())) DEFERRED.add(player.getUUID()); else ensureOpen(player);
        return true;
    }

    /** The scouting state changed for this player: refresh the panel, opening the overlay when an encounter begins. */
    public static void pushScout(ServerPlayer player, ScoutPayloads.State state) {
        InspectWeb.onScout(player, state);
        if (!WebUi.canShow(player)) return;
        if (state.entries().isEmpty() && !OPEN.contains(player.getUUID())) return;
        if (!state.entries().isEmpty()) ensureOpen(player);
        if (live(player)) {
            WebUi.emit(player, "hudScout", Views.state(state));
            if (!state.entries().isEmpty()) sendTeam(player);
        }
    }

    /** The bleeds in this player's battle changed: show the badge strip, opening the overlay for the first one. An empty list clears it. */
    public static void pushBleed(ServerPlayer player, List<Views.BleedView> bleeds) {
        if (bleeds.isEmpty()) BLEEDS.remove(player.getUUID()); else BLEEDS.put(player.getUUID(), List.copyOf(bleeds));
        if (!WebUi.canShow(player)) return;
        if (bleeds.isEmpty() && !OPEN.contains(player.getUUID())) return;
        if (!bleeds.isEmpty()) ensureOpen(player);
        if (live(player)) WebUi.emit(player, "hudBleed", bleeds);
    }

    /**
     * Momentum stacks or a Wardstone/Ensnaring proc in this player's battle: show the strip, opening the overlay for the first one. Stacks stay until
     * an update without them; a proc is flashed once and not kept (an overlay that is still loading misses it, the stacks are re-sent).
     */
    public static void pushEffects(ServerPlayer player, Views.EffectsView effects) {
        if (effects.momentum().isEmpty()) MOMENTUM.remove(player.getUUID()); else MOMENTUM.put(player.getUUID(), List.copyOf(effects.momentum()));
        if (!WebUi.canShow(player)) return;
        boolean any = !effects.momentum().isEmpty() || !effects.procs().isEmpty();
        if (!any && !OPEN.contains(player.getUUID())) return;
        if (any) ensureOpen(player);
        if (live(player)) WebUi.emit(player, "hudEffects", effects);
    }

    /** Whether the overlay is loaded and in front, so an event sent now reaches it (a full-screen page in front would receive it instead). */
    private static boolean live(ServerPlayer player) {
        if (!OPEN.contains(player.getUUID())) return false;
        if (!GuiPresence.isOpen(player.getUUID())) return true;
        DEFERRED.add(player.getUUID());   // re-sent by flush() once the page closes
        return false;
    }

    private static void sendTeam(ServerPlayer player) {
        var team = team(player);
        WebSprites.feed(player, team.stream().map(Views.TeamView::speciesId).toList());
        WebUi.emit(player, "hudTeam", team);
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
