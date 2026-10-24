package com.ascensionlib.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players asked for calm effects: no animation, flash, glow or flare on AscensionLib's pages, for people who are sensitive to them (photosensitive
 * epilepsy, migraine, motion sickness). Off for everyone until a player turns it on. Kept in one small JSON file beside the world so the choice follows the
 * player across sessions and devices; a missing or unreadable file means nobody is calm and nothing else is affected. Other mods read it through
 * {@code AscensionApi.calmEffects}. Pure Java, no Minecraft types, so it is tested on its own.
 */
public final class CalmPrefs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Set<UUID> calm = ConcurrentHashMap.newKeySet();

    public CalmPrefs(Path file) {
        this.file = file;
        load();
    }

    public boolean get(UUID player) { return calm.contains(player); }

    /** Stores the choice and returns it. Off is the default and is not stored. */
    public synchronized boolean set(UUID player, boolean on) {
        if (on) calm.add(player); else calm.remove(player);
        save();
        return on;
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            JsonArray root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonArray.class);
            if (root == null) return;
            for (var entry : root) {
                try {
                    calm.add(UUID.fromString(entry.getAsString()));
                } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException skipped) {
                    // One bad entry is dropped; the rest still load.
                }
            }
        } catch (IOException | JsonParseException unreadable) {
            calm.clear();
        }
    }

    /** Written to a temporary file and moved into place, so a crash never leaves half a file. */
    private void save() {
        if (file == null) return;
        try {
            var sorted = new TreeSet<String>();
            calm.forEach(id -> sorted.add(id.toString()));
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(sorted), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException failed) {
            // The choice still holds until the server restarts; saving is best effort and must never break a page.
        }
    }
}
