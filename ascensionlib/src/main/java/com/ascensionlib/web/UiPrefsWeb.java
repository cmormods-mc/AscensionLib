package com.ascensionlib.web;

import com.ascensionlib.net.RateLimit;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The page size choice ({@link UiPrefs}) for the pages: a page asks for its player's size when it loads ({@code ascension_ui_get}) and sends a new one from
 * its header control ({@code ascension_ui_set}); both are answered with {@code uiScale}. The server clamps and stores; the page only draws.
 */
public final class UiPrefsWeb {
    public record Scale(int percent, int min, int max, boolean calm) {}

    private static UiPrefs prefs;
    private static Path prefsFile;

    private UiPrefsWeb() {}

    public static void register() {
        WebUi.listen("ascension_ui_get", (player, o) -> {
            if (RateLimit.allow(player.getUUID(), "ui.get", 8, 4)) send(player);
        });
        WebUi.listen("ascension_ui_set", (player, o) -> {
            if (!RateLimit.allow(player.getUUID(), "ui.set", 8, 4)) return;
            long value = WebUi.num(o, "percent");
            boolean changed = false;
            if (value >= 0) { of(player.getServer()).set(player.getUUID(), value); changed = true; }
            if (o.has("calm")) { calmOf(player.getServer()).set(player.getUUID(), WebUi.bool(o, "calm")); changed = true; }
            if (changed) send(player);
        });
    }

    /** The prefs of this server's world, loaded once (and again if the world changed). */
    public static synchronized UiPrefs of(MinecraftServer server) {
        Path file = server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve("ascensionlib-ui.json");
        if (prefs == null || (file != null && !file.equals(prefsFile))) {
            prefs = new UiPrefs(file);
            prefsFile = file;
        }
        return prefs;
    }

    private static CalmPrefs calm;
    private static Path calmFile;

    /** The calm-effects choices of this server's world (see {@link CalmPrefs}), loaded once and again if the world changed. */
    public static synchronized CalmPrefs calmOf(MinecraftServer server) {
        Path file = server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve("ascensionlib-calm.json");
        if (calm == null || (file != null && !file.equals(calmFile))) {
            calm = new CalmPrefs(file);
            calmFile = file;
        }
        return calm;
    }

    public static boolean calmOf(ServerPlayer player) { return calmOf(player.getServer()).get(player.getUUID()); }

    public static int percentOf(ServerPlayer player) { return of(player.getServer()).get(player.getUUID()); }

    public static void send(ServerPlayer player) {
        WebUi.emit(player, "uiScale", new Scale(percentOf(player), UiPrefs.MIN_PERCENT, UiPrefs.MAX_PERCENT, calmOf(player)));
    }
}
