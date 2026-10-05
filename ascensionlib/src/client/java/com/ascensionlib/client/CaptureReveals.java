package com.ascensionlib.client;

import com.ascensionlib.scout.CaptureRevealPayload;
import com.cobblemon.mod.common.client.CobblemonClient;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Receives capture outcomes and decides when to show them. The server has already saved every outcome, so nothing here can
 * lose or change one: a reveal waits until no screen (and no battle) is up, results never replace each other, and when the
 * queue is full the overflow is collapsed into a chat line and the recent list (the inspect button reaches every Pokemon).
 */
final class CaptureReveals {
    static final int MAX_QUEUE = 8;
    private static final Deque<CaptureRevealPayload> QUEUE = new ArrayDeque<>();
    private static final List<CaptureRevealPayload> RECENT = new ArrayList<>();
    private static long notBefore;
    private static int overflow;

    private CaptureReveals() {}

    static void accept(CaptureRevealPayload payload) {
        // A result already waiting in the queue is not queued twice; replaying one that was shown is presentation only.
        for (var waiting : QUEUE) if (waiting.pokemonId().equals(payload.pokemonId())) return;
        RECENT.removeIf(seen -> seen.pokemonId().equals(payload.pokemonId()));
        RECENT.add(payload);
        if (RECENT.size() > 20) RECENT.remove(0);
        var mc = Minecraft.getInstance();
        switch (AscensionClientSettings.reveal) {
            case OFF -> { }
            case COMPACT -> toast(mc, payload);
            case FULL -> {
                if (QUEUE.size() >= MAX_QUEUE) overflow++;
                else QUEUE.add(payload);
            }
        }
    }

    static void toast(Minecraft mc, CaptureRevealPayload payload) {
        var d = payload.detail();
        String rarity = d.rarityId().equals("mythical") ? "Mythic" : d.rarityId().substring(0, 1).toUpperCase() + d.rarityId().substring(1);
        String affix = d.slots().isEmpty() ? "" : " · " + d.slots().get(0).name() + " +" + d.slots().get(0).value() + "%";
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal(rarity + " ascension"), Component.literal(payload.name() + affix));
    }

    static void tick(Minecraft mc) {
        if (QUEUE.isEmpty() || mc.player == null || mc.screen != null) return;
        if (CobblemonClient.INSTANCE.getBattle() != null) return;          // never over a battle or another essential screen
        if (System.currentTimeMillis() < notBefore) return;
        if (overflow > 0 && QUEUE.size() >= MAX_QUEUE) {
            mc.player.displayClientMessage(Component.literal("[Ascend] " + overflow + " more capture(s) saved; inspect them from the Pokémon summary."), false);
            overflow = 0;
        }
        mc.setScreen(new CaptureRevealScreen(QUEUE.poll()));
    }

    /** A short gap between two reveals so the player can act on the world and never gets a screen yanked back at once. */
    static void closed() { notBefore = System.currentTimeMillis() + 600; }

    static void clear() { QUEUE.clear(); RECENT.clear(); overflow = 0; CardSprites.clear(); }
}
