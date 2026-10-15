package com.ascensionlib.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's chosen size for AscensionLib's pages, as a percent of the automatic fit (100 is what the page picks for the window). The page applies it on
 * top of its own fit, so it works on any screen. Kept in one small JSON file beside the world so it follows the player across sessions and devices; a missing or
 * unreadable file means everyone is at 100 and nothing else is affected. Pure Java, no Minecraft types, so it is tested on its own.
 */
public final class UiPrefs {
    public static final int MIN_PERCENT = 60;
    public static final int MAX_PERCENT = 160;
    public static final int DEFAULT_PERCENT = 100;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<UUID, Integer> percent = new ConcurrentHashMap<>();

    public UiPrefs(Path file) {
        this.file = file;
        load();
    }

    public static int clamp(long value) {
        return (int) Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, value));
    }

    public int get(UUID player) {
        return percent.getOrDefault(player, DEFAULT_PERCENT);
    }

    /** Stores the clamped value (100 is not stored: it is the default) and returns it. */
    public synchronized int set(UUID player, long value) {
        int clamped = clamp(value);
        if (clamped == DEFAULT_PERCENT) percent.remove(player); else percent.put(player, clamped);
        save();
        return clamped;
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return;
            for (var entry : root.entrySet()) {
                try {
                    percent.put(UUID.fromString(entry.getKey()), clamp(entry.getValue().getAsLong()));
                } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException skipped) {
                    // One bad entry is dropped; the rest still load.
                }
            }
        } catch (IOException | JsonParseException unreadable) {
            percent.clear();
        }
    }

    /** Written to a temporary file and moved into place, so a crash never leaves half a file. */
    private void save() {
        if (file == null) return;
        try {
            var sorted = new TreeMap<String, Integer>();
            percent.forEach((id, value) -> sorted.put(id.toString(), value));
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(sorted), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException failed) {
            // The choice still holds until the server restarts; saving is best effort and must never break a page.
        }
    }
}
