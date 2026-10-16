package com.cobbleascend.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Dated snapshots of the progression store in one folder (the world's {@code ascensionlib-backups/}): a consistent copy of the live database made by SQLite
 * itself ({@link ProgressionStore#backupTo}), so it is safe while the server runs. A snapshot is taken at server start and then once a day, and on demand;
 * only the newest few are kept. The library also copies the store before it upgrades its schema (see {@code ProgressionStore.open}), because a newer store cannot
 * be opened by an older mod: that copy is the way back.
 */
public final class StoreBackups {
    /** How many snapshots are kept. */
    public static final int KEEP = 14;
    /** How old the newest snapshot may be before another is due. */
    public static final Duration INTERVAL = Duration.ofHours(24);

    static final String PREFIX = "progression-";
    static final String SUFFIX = ".db";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private StoreBackups() {}

    static String nameAt(java.time.Instant at) { return PREFIX + STAMP.format(at) + SUFFIX; }

    /** The snapshots in {@code dir}, oldest first (the stamp in the name sorts as time). */
    public static List<Path> list(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            var found = new ArrayList<>(files.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(PREFIX) && n.endsWith(SUFFIX);
            }).sorted().toList());
            return found;
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    /** Whether the newest snapshot in {@code dir} is older than {@link #INTERVAL} (or there is none). */
    public static boolean due(Path dir, Clock clock) {
        var all = list(dir);
        if (all.isEmpty()) return true;
        try {
            var newest = Files.getLastModifiedTime(all.get(all.size() - 1)).toInstant();
            return Duration.between(newest, clock.instant()).compareTo(INTERVAL) >= 0;
        } catch (IOException unreadable) {
            return true;
        }
    }

    /**
     * Takes a snapshot named for the clock's time into {@code dir} and deletes the oldest beyond {@link #KEEP}. Two snapshots in the same second share a
     * name, so the second returns the first.
     *
     * @return the snapshot file
     */
    public static Path backup(ProgressionStore store, Path dir, Clock clock) {
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            throw new StoreException(StoreException.Code.STORE_UNAVAILABLE, "Cannot create the backup folder " + dir, exception);
        }
        Path target = dir.resolve(nameAt(clock.instant()));
        if (!Files.exists(target)) store.backupTo(target);
        var all = list(dir);
        for (int i = 0; i < all.size() - KEEP; i++) {
            try {
                Files.deleteIfExists(all.get(i));
            } catch (IOException ignored) {
                // an old snapshot that cannot be deleted now is tried again next time
            }
        }
        return target;
    }

    /** A snapshot only if one is due; empty otherwise. */
    public static Optional<Path> backupIfDue(ProgressionStore store, Path dir, Clock clock) {
        return due(dir, clock) ? Optional.of(backup(store, dir, clock)) : Optional.empty();
    }
}
