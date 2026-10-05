package com.ascensionlib.client;

import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Client-only presentation settings for the capture reveal. Nothing here affects gameplay or what the server sends. */
final class AscensionClientSettings {
    enum RevealMode { FULL, COMPACT, OFF }

    static RevealMode reveal = RevealMode.FULL;
    static boolean reducedMotion = false;
    static boolean sounds = true;

    private AscensionClientSettings() {}

    private static java.nio.file.Path file() { return FabricLoader.getInstance().getConfigDir().resolve("ascensionlib-client.properties"); }

    static void load() {
        if (!Files.exists(file())) return;
        try (var reader = Files.newBufferedReader(file())) {
            var p = new Properties();
            p.load(reader);
            try { reveal = RevealMode.valueOf(p.getProperty("reveal", "full").toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { reveal = RevealMode.FULL; }
            reducedMotion = Boolean.parseBoolean(p.getProperty("reducedMotion", "false"));
            sounds = Boolean.parseBoolean(p.getProperty("sounds", "true"));
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger("ascensionlib").warn("Cannot read client settings", e);
        }
    }

    static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (var writer = Files.newBufferedWriter(file())) {
                var p = new Properties();
                p.setProperty("reveal", reveal.name().toLowerCase(Locale.ROOT));
                p.setProperty("reducedMotion", Boolean.toString(reducedMotion));
                p.setProperty("sounds", Boolean.toString(sounds));
                p.store(writer, "AscensionLib client presentation only");
            }
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger("ascensionlib").warn("Cannot save client settings", e);
        }
    }
}
