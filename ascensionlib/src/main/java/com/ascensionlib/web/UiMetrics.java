package com.ascensionlib.web;

import com.ascensionlib.net.RateLimit;
import com.ascensionlib.telemetry.BalanceLog;
import com.google.gson.JsonArray;
import java.util.regex.Pattern;

/**
 * The page's own stopwatch, reported to the server: how long it took to get its first data, how long each server event took to draw, and how many
 * long tasks the browser saw ({@code A.perf} in {@code ui.js}). The server cannot see any of this, and it is what a player feels as "the screen is slow".
 * A page batches its samples and sends {@code ascension_perf} every 30 seconds and when it closes.
 *
 * <p>The page is code the player can edit, so everything is bounded here: page and label names must be plain words, a message carries at most
 * {@value #MAX_LABELS} labels of {@value #MAX_SAMPLES} samples, a sample is clamped to a minute, and a player may send only a few messages a minute.
 * A false number from a hacked page can only skew that one label of the performance log; nothing else reads it.
 */
public final class UiMetrics {
    private static final Pattern NAME = Pattern.compile("[a-z][a-zA-Z0-9.]{0,23}");
    static final int MAX_LABELS = 12, MAX_SAMPLES = 20;
    static final double MAX_MS = 60_000;

    private UiMetrics() {}

    public static void register() {
        WebUi.listen("ascension_perf", (player, o) -> {
            if (!RateLimit.allow(player.getUUID(), "ui.perf", 3, 0.1)) return;
            String page = WebUi.str(o, "page", 24);
            var timings = o.get("t");
            if (!NAME.matcher(page).matches() || timings == null || !timings.isJsonObject()) return;
            int labels = 0;
            for (var entry : timings.getAsJsonObject().entrySet()) {
                if (labels++ >= MAX_LABELS) break;
                if (!NAME.matcher(entry.getKey()).matches() || !entry.getValue().isJsonArray()) continue;
                for (double ms : samples(entry.getValue().getAsJsonArray())) BalanceLog.pageTimings(page, entry.getKey(), ms);
            }
        });
    }

    /** The numeric samples of a page's array, at most {@value #MAX_SAMPLES}, each clamped to 0..a minute; anything else in it is skipped. */
    static double[] samples(JsonArray array) {
        double[] out = new double[Math.min(array.size(), MAX_SAMPLES)];
        int n = 0;
        for (int i = 0; i < out.length; i++) {
            var item = array.get(i);
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) continue;
            double ms = item.getAsDouble();
            if (Double.isNaN(ms) || Double.isInfinite(ms)) continue;
            out[n++] = Math.max(0, Math.min(MAX_MS, ms));
        }
        return java.util.Arrays.copyOf(out, n);
    }
}
