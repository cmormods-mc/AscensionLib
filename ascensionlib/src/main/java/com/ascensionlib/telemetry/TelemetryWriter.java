package com.ascensionlib.telemetry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Appends one JSON line per event to {@code events-YYYY-MM-DD.jsonl} (UTC day) from its own thread, so the server thread never waits on the disk: a caller
 * hands a line over with {@link #offer} and gets back at once. The queue is bounded and a full one drops the line and counts it (reported as a
 * {@code dropped} event when the writer next has room), because losing a log line is better than stalling a tick. A day's file stops growing at
 * {@link #DAY_LIMIT_BYTES}; files older than the retention are deleted when the writer opens.
 */
final class TelemetryWriter implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");
    static final int QUEUE_LIMIT = 20_000;
    static final long DAY_LIMIT_BYTES = 128L * 1024 * 1024;
    private static final String PREFIX = "events-", SUFFIX = ".jsonl";

    private final Path dir;
    private final LongSupplier clock;
    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(QUEUE_LIMIT);
    private final AtomicLong dropped = new AtomicLong();
    private final Thread thread;
    private volatile boolean closing;

    private LocalDate day;
    private BufferedWriter out;
    private long bytes;
    private boolean fullNoticeGiven;

    TelemetryWriter(Path dir, LongSupplier clock, int retentionDays) throws IOException {
        this.dir = dir;
        this.clock = clock;
        Files.createDirectories(dir);
        prune(retentionDays);
        thread = new Thread(this::run, "ascensionlib-telemetry");
        thread.setDaemon(true);
        thread.start();
    }

    /** Hands over one finished JSON line. Never blocks; returns false (and counts a drop) when the queue is full or the writer is closing. */
    boolean offer(String line) {
        if (closing || !queue.offer(line)) {
            dropped.incrementAndGet();
            return false;
        }
        return true;
    }

    long dropped() { return dropped.get(); }

    static String fileName(LocalDate day) { return PREFIX + day + SUFFIX; }

    private void run() {
        try {
            while (!closing || !queue.isEmpty()) {
                String line = queue.poll(500, TimeUnit.MILLISECONDS);
                if (line == null) { flush(); continue; }
                write(line);
                long lost = dropped.getAndSet(0);
                if (lost > 0) write("{\"t\":" + clock.getAsLong() + ",\"type\":\"dropped\",\"lines\":" + lost + "}");
                if (queue.isEmpty()) flush();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            flush();
            closeFile();
        }
    }

    private void write(String line) {
        try {
            var today = Instant.ofEpochMilli(clock.getAsLong()).atZone(ZoneOffset.UTC).toLocalDate();
            if (!today.equals(day) || out == null) open(today);
            long size = line.length() + 1L;     // characters, not bytes: the limit is a guard against a runaway log, not a quota
            if (bytes + size > DAY_LIMIT_BYTES) {
                if (!fullNoticeGiven) {
                    fullNoticeGiven = true;
                    LOG.warn("Telemetry file {} reached {} MB; the rest of the day is not logged", fileName(today), DAY_LIMIT_BYTES >> 20);
                }
                return;
            }
            out.write(line);
            out.write('\n');
            bytes += size;
        } catch (IOException exception) {
            LOG.warn("Telemetry write failed ({}); the line is lost", exception.toString());
            closeFile();
        }
    }

    private void open(LocalDate today) throws IOException {
        closeFile();
        Path file = dir.resolve(fileName(today));
        bytes = Files.exists(file) ? Files.size(file) : 0L;
        out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        day = today;
        fullNoticeGiven = false;
    }

    private void flush() {
        try { if (out != null) out.flush(); } catch (IOException exception) { closeFile(); }
    }

    private void closeFile() {
        try { if (out != null) out.close(); } catch (IOException ignored) { }
        out = null;
    }

    /** Deletes event files whose date is more than {@code retentionDays} before today; files that do not parse are left alone. */
    private void prune(int retentionDays) {
        var oldest = Instant.ofEpochMilli(clock.getAsLong()).atZone(ZoneOffset.UTC).toLocalDate().minusDays(retentionDays);
        try (var files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) continue;
                try {
                    if (LocalDate.parse(name.substring(PREFIX.length(), name.length() - SUFFIX.length())).isBefore(oldest)) Files.deleteIfExists(file);
                } catch (java.time.format.DateTimeParseException notOurs) {
                    // not a file this writer made
                }
            }
        } catch (IOException exception) {
            LOG.warn("Could not clear old telemetry files: {}", exception.toString());
        }
    }

    /** Writes what is queued and stops the thread (waits up to five seconds). */
    @Override public void close() {
        closing = true;
        try {
            thread.join(5000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
