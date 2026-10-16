package com.ascensionlib;

import com.cobbleascend.store.StoreBackups;
import java.nio.file.Path;
import java.time.Clock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps dated snapshots of the progression store in {@code <world>/cobbleascend/backups/}: one when the server has started, then one whenever the newest is more
 * than a day old (checked every ten minutes), and one on demand ({@code /ascend admin backup}). Only the newest {@link StoreBackups#KEEP} are kept. The copy is
 * made by SQLite while the store is live, takes a moment on a small database and fails closed: a failure is logged and nothing else changes.
 */
public final class BackupJob {
    private static final Logger LOG = LoggerFactory.getLogger(AscensionLib.MOD_ID);
    private static final int CHECK_TICKS = 20 * 60 * 10;

    private BackupJob() {}

    static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(AscensionRuntime.DATA_DIRECTORY).resolve("backups");
    }

    /** Called every server tick; looks at the clock only every ten minutes. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_TICKS != 0) return;
        backupIfDue(server);
    }

    /** At server start: the first snapshot of the day is taken now rather than ten minutes in. */
    public static void started(MinecraftServer server) { backupIfDue(server); }

    private static void backupIfDue(MinecraftServer server) {
        var store = AscensionApi.store().orElse(null);
        if (store == null) return;
        try {
            StoreBackups.backupIfDue(store, dir(server), Clock.systemUTC()).ifPresent(path -> LOG.info("Progression store backed up to {}", path));
        } catch (RuntimeException exception) {
            LOG.warn("Progression store backup failed: {}", exception.toString());
        }
    }

    /** Takes a snapshot now. Returns its path, or empty when the store is unavailable or the copy failed (the reason is logged). */
    public static java.util.Optional<Path> now(MinecraftServer server) {
        var store = AscensionApi.store().orElse(null);
        if (store == null) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(StoreBackups.backup(store, dir(server), Clock.systemUTC()));
        } catch (RuntimeException exception) {
            LOG.warn("Progression store backup failed: {}", exception.toString());
            return java.util.Optional.empty();
        }
    }
}
