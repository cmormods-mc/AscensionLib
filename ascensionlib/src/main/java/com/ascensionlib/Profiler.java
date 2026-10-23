package com.ascensionlib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's stopwatch: named timings (the server thread's tick, a store transaction, a page request, a page's own render time as the page reports it)
 * and named sizes (bytes of a page event), summarised as count, average, 95th percentile and maximum.
 *
 * <p>It is always on, because a measurement that has to be switched on is missing on the day it is needed; a sample costs one clock read and a short
 * lock. {@link #drain} hands the summaries over and starts a fresh window: {@code BalanceLog} calls it once a minute and writes the result to the
 * telemetry file. Setting {@code -Dascensionlib.profile=true}, {@code ASCENSIONLIB_PROFILE=1} or an empty {@code config/ascensionlib-profile.flag}
 * additionally prints every summary to the server log.
 */
public final class Profiler {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");
    /** The most recent samples kept per label for the percentile; the count, total and maximum cover all of them. */
    private static final int KEEP = 512;
    /** A page can only reach labels the server named, but a bug must not grow the table without limit. */
    private static final int MAX_LABELS = 256;

    private static final Map<String, Stat> STATS = new HashMap<>();

    private static final class Stat {
        final boolean time;
        long count, total, max;
        final long[] recent = new long[KEEP];
        Stat(boolean time) { this.time = time; }
    }

    /** One label's window. {@code unit} is {@code "ms"} for timings and {@code "n"} for sizes and counts. */
    public record Summary(String label, String unit, long count, double avg, double p95, double max) {}

    private Profiler() {}

    /** Starts a measurement; pass the result to {@link #stop}. */
    public static long start() { return System.nanoTime(); }

    public static void stop(String label, long startedAt) { record(label, System.nanoTime() - startedAt); }

    /** Records one duration in nanoseconds. */
    public static void record(String label, long nanos) { add(label, true, Math.max(0, nanos)); }

    /** Records one size or count (for example the characters of a page event). */
    public static void sample(String label, long value) { add(label, false, Math.max(0, value)); }

    private static synchronized void add(String label, boolean time, long value) {
        var stat = STATS.get(label);
        if (stat == null) {
            if (STATS.size() >= MAX_LABELS) return;
            stat = new Stat(time);
            STATS.put(label, stat);
        }
        stat.recent[(int) (stat.count % KEEP)] = value;
        stat.count++;
        stat.total += value;
        stat.max = Math.max(stat.max, value);
    }

    /** The summaries of everything recorded since the last call, busiest label first, and a fresh window. */
    public static synchronized List<Summary> drain() {
        var out = new ArrayList<Summary>(STATS.size());
        for (var entry : STATS.entrySet()) {
            var s = entry.getValue();
            if (s.count == 0) continue;
            int n = (int) Math.min(s.count, KEEP);
            long[] sorted = Arrays.copyOf(s.recent, n);
            Arrays.sort(sorted);
            long p95 = sorted[Math.min(n - 1, (int) Math.ceil(n * 0.95) - 1)];
            double scale = s.time ? 1e6 : 1.0;
            out.add(new Summary(entry.getKey(), s.time ? "ms" : "n", s.count, s.total / (double) s.count / scale, p95 / scale, s.max / scale));
        }
        STATS.clear();
        out.sort(Comparator.comparingLong(Summary::count).reversed().thenComparing(Summary::label));
        if (LOG_SUMMARIES) for (var s : out)
            LOG.info("[profile] {}: n={} avg={}{} p95={}{} max={}{}", s.label(), s.count(), round(s.avg()), s.unit(), round(s.p95()), s.unit(), round(s.max()), s.unit());
        return out;
    }

    /** Three decimals, enough to tell a 0.04 ms call from a 0.4 ms one. */
    public static double round(double value) { return Math.round(value * 1000.0) / 1000.0; }

    private static final boolean LOG_SUMMARIES = Boolean.getBoolean("ascensionlib.profile") || "1".equals(System.getenv("ASCENSIONLIB_PROFILE")) || flagFile();

    /** For hosts with no way to edit JVM arguments: an empty file {@code config/ascensionlib-profile.flag} turns the log lines on at the next start. */
    private static boolean flagFile() {
        try {
            return java.nio.file.Files.exists(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ascensionlib-profile.flag"));
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }
}
