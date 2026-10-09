package com.ascensionlib;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An opt-in stopwatch for the screens and the server paths behind them. Off unless {@code -Dascensionlib.profile=true}, the
 * environment variable {@code ASCENSIONLIB_PROFILE=1}, the file {@code config/ascensionlib-profile.flag}, or the client setting {@code uiProfile=true}; when off every call is a
 * single boolean test. When on, each label's samples are summarised in the log every five seconds as count, average, 95th
 * percentile and maximum in milliseconds, so a slow frame or a slow server reply shows up as a number instead of a feeling.
 */
public final class Profiler {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");
    private static final long FLUSH_NANOS = 5_000_000_000L;
    private static final int KEEP = 1024;

    public static volatile boolean on = Boolean.getBoolean("ascensionlib.profile") || "1".equals(System.getenv("ASCENSIONLIB_PROFILE")) || flagFile();

    /** For hosts with no way to edit JVM arguments: an empty file {@code config/ascensionlib-profile.flag} turns it on at the next start. */
    private static boolean flagFile() {
        try {
            return java.nio.file.Files.exists(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ascensionlib-profile.flag"));
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static final Map<String, Stat> STATS = new HashMap<>();
    private static final Map<String, Long> LAST_FRAME = new HashMap<>();
    private static long lastFlush = System.nanoTime();

    private static final class Stat {
        long count, total, max;
        final long[] recent = new long[KEEP];
    }

    private Profiler() {}

    /** Starts a measurement; pass the result to {@link #stop}. */
    public static long start() { return on ? System.nanoTime() : 0L; }

    public static void stop(String label, long startedAt) {
        if (on && startedAt != 0L) record(label, System.nanoTime() - startedAt);
    }

    private static final com.sun.management.ThreadMXBean THREADS = threads();

    private static com.sun.management.ThreadMXBean threads() {
        try {
            return java.lang.management.ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean t ? t : null;
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** Bytes this thread has allocated so far, whether or not profiling is on; 0 when the JVM cannot say. */
    public static long allocatedBytes() {
        return THREADS != null ? THREADS.getCurrentThreadAllocatedBytes() : 0L;
    }

    /** Bytes this thread has allocated so far, or 0 when profiling is off or the JVM cannot say; pair with {@link #stopAlloc}. */
    public static long startAlloc() {
        return on && THREADS != null ? THREADS.getCurrentThreadAllocatedBytes() : 0L;
    }

    /** Records the bytes allocated since {@link #startAlloc} under {@code label}; it is reported in KB, not ms. */
    public static void stopAlloc(String label, long before) {
        if (on && THREADS != null && before != 0L) record(label + ".allocKB", THREADS.getCurrentThreadAllocatedBytes() - before);
    }

    /** Call once per rendered frame of a screen: records the time since the previous frame, i.e. the whole frame, world included. */
    public static void frame(String label) {
        if (!on) return;
        long now = System.nanoTime();
        synchronized (Profiler.class) {
            Long last = LAST_FRAME.put(label, now);
            if (last != null && now - last < 1_000_000_000L) recordLocked(label, now - last);
        }
    }

    /** Forget frame timing, so the first frame after a screen opens is not measured against the last time it was open. */
    public static void resetFrames() {
        if (!on) return;
        synchronized (Profiler.class) { LAST_FRAME.clear(); }
    }

    public static synchronized void record(String label, long nanos) { recordLocked(label, nanos); }

    private static void recordLocked(String label, long nanos) {
        var stat = STATS.computeIfAbsent(label, k -> new Stat());
        stat.recent[(int) (stat.count % KEEP)] = nanos;
        stat.count++;
        stat.total += nanos;
        stat.max = Math.max(stat.max, nanos);
        long now = System.nanoTime();
        if (now - lastFlush >= FLUSH_NANOS) { lastFlush = now; flushLocked(); }
    }

    private static void flushLocked() {
        for (var entry : new java.util.TreeMap<>(STATS).entrySet()) {
            var s = entry.getValue();
            if (s.count == 0) continue;
            int n = (int) Math.min(s.count, KEEP);
            long[] sorted = Arrays.copyOf(s.recent, n);
            Arrays.sort(sorted);
            long p95 = sorted[Math.min(n - 1, (int) Math.ceil(n * 0.95) - 1)];
            if (entry.getKey().endsWith(".allocKB")) {
                LOG.info("[profile] {}: n={} avg={}KB p95={}KB max={}KB", entry.getKey(), s.count, s.total / s.count / 1024, p95 / 1024, s.max / 1024);
                continue;
            }
            LOG.info("[profile] {}: n={} avg={}ms p95={}ms max={}ms", entry.getKey(), s.count,
                    String.format("%.3f", s.total / s.count / 1e6), String.format("%.3f", p95 / 1e6), String.format("%.3f", s.max / 1e6));
        }
        var rt = Runtime.getRuntime();
        LOG.info("[profile] heap: used={}MB max={}MB", (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20);
        STATS.clear();
    }

    /** Writes out whatever is pending, e.g. when a screen closes. */
    public static synchronized void flush() {
        if (on) { lastFlush = System.nanoTime(); flushLocked(); }
    }
}
