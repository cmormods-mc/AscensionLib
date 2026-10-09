package com.ascensionlib.client;

import com.ascensionlib.fusion.FusionPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client side of the fusion channel: asks for a host's candidate donors and routes the server's answers to the open screen (or opens
 * it, when a command or a button asked). The client holds nothing that matters: every name, number and price on the screen is text the
 * server sent, and nothing happens until the server accepts a confirmation.
 */
final class FusionClient {
    private static Screen pendingParent;
    private static boolean waitingToOpen;
    private static long waitStartedNanos;
    private static final long WAIT_NANOS = 10_000_000_000L;

    private FusionClient() {}

    /** Asks for the candidates; the screen opens when they arrive, returning to {@code parent} when closed. */
    static void open(Screen parent, String hostId) {
        if (!ClientPlayNetworking.canSend(FusionPayloads.Open.TYPE)) return;
        pendingParent = parent;
        waitingToOpen = true;
        waitStartedNanos = System.nanoTime();
        ClientPlayNetworking.send(new FusionPayloads.Open(hostId));
    }

    static void clear() {
        pendingParent = null;
        waitingToOpen = false;
    }

    static void onCandidates(FusionPayloads.Candidates candidates) {
        var mc = Minecraft.getInstance();
        if (waitingToOpen && System.nanoTime() - waitStartedNanos > WAIT_NANOS) clear();
        if (mc.screen instanceof FusionScreen screen && screen.hostId().equals(candidates.hostId())) {
            screen.accept(candidates);
        } else if (waitingToOpen || mc.screen == null) {
            mc.setScreen(new FusionScreen(waitingToOpen ? pendingParent : mc.screen, candidates));
        }
        waitingToOpen = false;
        pendingParent = null;
    }

    static void onDetail(FusionPayloads.Detail detail) {
        if (Minecraft.getInstance().screen instanceof FusionScreen screen) screen.detail(detail);
    }

    static void onResult(FusionPayloads.Result result) {
        if (Minecraft.getInstance().screen instanceof FusionScreen screen) screen.result(result);
    }
}
