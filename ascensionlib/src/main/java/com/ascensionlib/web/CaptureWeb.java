package com.ascensionlib.web;

import com.ascensionlib.net.RateLimit;
import com.ascensionlib.scout.CaptureRevealPayload;
import com.ascensionlib.scout.InspectWeb;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Transparent capture GUI: WebGUI owns cursor unlock and restores Minecraft input when its screen closes. */
public final class CaptureWeb {
    private static final Map<UUID, CaptureRevealQueue> QUEUES = new ConcurrentHashMap<>();
    private static RevealPrefs prefs;
    private static Path prefsFile;

    private CaptureWeb() {}

    private static synchronized RevealPrefs prefs(MinecraftServer server) {
        Path file = server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve("ascensionlib-reveals.json");
        if (prefs == null || (file != null && !file.equals(prefsFile))) { prefs = new RevealPrefs(file); prefsFile = file; }
        return prefs;
    }
    public static boolean enabled(ServerPlayer player) { return prefs(player.getServer()).enabled(player.getUUID()); }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        prefs(player.getServer()).setEnabled(player.getUUID(), enabled);
        if (!enabled) {
            var queue = QUEUES.get(player.getUUID());
            boolean showing = queue != null && queue.active() != null;
            if (queue != null) queue.clear();
            if (showing) WebUi.emit(player, "captureClosed", true);
        }
    }

    public static void register() {
        WebUi.listen("ascension_capture_ready", (player, o) -> {
            var queue = QUEUES.get(player.getUUID());
            if (!enabled(player) || queue == null || queue.active() == null) { WebUi.emit(player, "captureClosed", true); return; }
            UiPrefsWeb.send(player);
            send(player, queue.active());
        });
        WebUi.listen("ascension_capture_dismiss", (player, o) -> {
            var queue = QUEUES.get(player.getUUID());
            if (queue == null || !queue.dismiss(WebUi.id(o, "revealId"))) return;
            if (queue.hasWaiting() && enabled(player)) send(player, queue.advance());
            else WebUi.emit(player, "captureClosed", true);
        });
        WebUi.listen("ascension_capture_closed", (player, o) -> {
            var queue = QUEUES.get(player.getUUID());
            if (queue != null) queue.dismiss(WebUi.id(o, "revealId"));
        });
        WebUi.listen("ascension_capture_disable", (player, o) -> {
            if (!RateLimit.allow(player.getUUID(), "capture.disable", 2, 1)) return;
            setEnabled(player, false);
            player.sendSystemMessage(Component.literal("[Ascend] Capture reveals disabled. Use /ascend reveals on to enable them."));
        });
        WebUi.listen("ascension_capture_open", (player, o) -> {
            if (!RateLimit.allow(player.getUUID(), "inspect.open", 4, 2)) return;
            var queue = QUEUES.get(player.getUUID());
            var active = queue == null ? null : queue.active();
            if (active == null || !active.pokemonId().equals(WebUi.id(o, "id")) || !active.revealId().equals(WebUi.id(o, "revealId"))) return;
            queue.dismiss(active.revealId());
            InspectWeb.open(player, InspectWeb.Kind.OWNED, active.pokemonId());
        });
        ServerTickEvents.END_SERVER_TICK.register(CaptureWeb::tick);
    }

    public static boolean reveal(ServerPlayer player, CaptureRevealPayload payload) {
        if (!enabled(player)) return true; // opted out: no card or redundant chat notification
        if (!WebUi.canShow(player)) return false;
        var queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new CaptureRevealQueue());
        if (!queue.offer(payload)) return false;
        openNext(player, queue);
        return true;
    }

    private static void openNext(ServerPlayer player, CaptureRevealQueue queue) {
        if (queue.active() != null || !queue.hasWaiting() || GuiPresence.isOpen(player.getUUID())) return;
        if (WebUi.open(player, "capture.html")) queue.advance();
    }

    private static void send(ServerPlayer player, CaptureRevealPayload payload) {
        WebSprites.feed(player, List.of(payload.speciesId()));
        WebUi.emit(player, "captureReveal", payload);
    }

    /** Escape or an unrelated screen stops the capture page's heartbeat; queued catches wait until that screen is gone. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        for (var entry : QUEUES.entrySet()) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) { forget(entry.getKey()); continue; }
            var queue = entry.getValue();
            if (queue.active() != null && !GuiPresence.isOpen(entry.getKey())) {
                queue.dismiss(queue.active().revealId());
            }
            if (enabled(player)) openNext(player, queue);
        }
    }
    public static void forget(UUID player) { QUEUES.remove(player); }
}
