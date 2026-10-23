package com.ascensionlib.telemetry;

import com.cobbleascend.domain.v1.MaterialId;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The telemetry log: structured events about how the mod is played and how it performs, written as JSON lines to
 * {@code <world>/cobbleascend/telemetry/events-YYYY-MM-DD.jsonl} so the economy can be balanced from what players actually do instead of from guesses.
 * Every line is an object with {@code t} (epoch milliseconds) and {@code type}; {@link BalanceLog} decides which events exist and what they carry.
 *
 * <p>It is on by default and costs the server thread one small object per event (the disk is written from another thread). Turn it off with
 * {@code -Dascensionlib.telemetry=false}, the environment variable {@code ASCENSIONLIB_TELEMETRY=0}, or an empty file
 * {@code config/ascensionlib-telemetry-off.flag}. Nothing here is ever sent anywhere; the files stay on the server.
 */
public final class Telemetry {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");
    private static final Gson GSON = new Gson();
    /** Days of event files kept; older ones are deleted at start. */
    public static final int RETENTION_DAYS = 30;

    private static volatile TelemetryWriter writer;

    private Telemetry() {}

    public static boolean enabled() { return writer != null; }

    /** Opens the log in {@code dir} unless the host turned it off. Safe to call again after {@link #close}. */
    public static synchronized void open(Path dir) {
        close();
        if (disabledByHost()) {
            LOG.info("Telemetry is switched off for this server");
            return;
        }
        try {
            writer = new TelemetryWriter(dir, System::currentTimeMillis, RETENTION_DAYS);
            LOG.info("Telemetry log: {} (kept {} days; switch off with -Dascensionlib.telemetry=false)", dir, RETENTION_DAYS);
        } catch (IOException | RuntimeException exception) {
            LOG.warn("Telemetry log could not be opened in {}; continuing without it: {}", dir, exception.toString());
        }
    }

    /** Writes what is queued and closes the log. */
    public static synchronized void close() {
        var current = writer;
        writer = null;
        if (current != null) current.close();
    }

    /**
     * Writes one event. {@code fill} adds the event's fields to an object that already holds {@code t} and {@code type}; it does not run at all while the
     * log is off, so building a field costs nothing then. A failure in {@code fill} is swallowed: telemetry must never break the thing it observes.
     */
    public static void log(String type, Consumer<JsonObject> fill) {
        var current = writer;
        if (current == null) return;
        try {
            var event = new JsonObject();
            event.addProperty("t", System.currentTimeMillis());
            event.addProperty("type", type);
            fill.accept(event);
            current.offer(GSON.toJson(event));
        } catch (RuntimeException exception) {
            LOG.debug("Telemetry event {} skipped: {}", type, exception.toString());
        }
    }

    /**
     * How a player is named in the log: 12 hex characters of a SHA-256 of the world's authority id and the UUID. The same player always gets the same key
     * within a world, so players can be counted and followed, but the files hold no UUID and a key cannot be turned back into one.
     */
    public static String playerKey(UUID player) {
        try {
            var salt = com.ascensionlib.AscensionApi.status().authority();
            var digest = java.security.MessageDigest.getInstance("SHA-256").digest(((salt == null ? "" : salt) + "|" + player).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 6);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A material map as {@code {"resonance_dust": 40}}, zero and negative amounts left out. */
    public static JsonObject materials(Map<MaterialId, Long> amounts) {
        var out = new JsonObject();
        amounts.forEach((id, amount) -> { if (amount != null && amount > 0) out.addProperty(id.id(), amount); });
        return out;
    }

    private static boolean disabledByHost() {
        if ("false".equalsIgnoreCase(System.getProperty("ascensionlib.telemetry")) || "0".equals(System.getenv("ASCENSIONLIB_TELEMETRY"))) return true;
        try {
            return Files.exists(FabricLoader.getInstance().getConfigDir().resolve("ascensionlib-telemetry-off.flag"));
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
