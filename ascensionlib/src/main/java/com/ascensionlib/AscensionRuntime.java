package com.ascensionlib;

import com.cobbleascend.domain.v1.RankedRules;
import com.cobbleascend.store.ProgressionStore;
import com.cobbleascend.store.StoreException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the canonical store for one running world. The store file and an {@code authority.id} marker live in
 * {@code <world>/}{@value #DATA_DIRECTORY}{@code /}. If the pairing is inconsistent (marker without store, store/marker authority
 * mismatch, unreadable store) the runtime stays disabled and nothing mutates: progression is never reset.
 */
public final class AscensionRuntime implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AscensionLib.MOD_ID);

    static final String DATA_DIRECTORY = "cobbleascend";

    private final ProgressionStore store;
    private final UUID authority;
    private final String disabledReason;

    private AscensionRuntime(ProgressionStore store, UUID authority, String disabledReason) {
        this.store = store;
        this.authority = authority;
        this.disabledReason = disabledReason;
    }

    public static AscensionRuntime open(MinecraftServer server, RankedRules rules) {
        // The directory keeps the name it had before the library was extracted, so existing worlds are found, not reset.
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve(DATA_DIRECTORY);
        Path database = dir.resolve("progression.db");
        Path marker = dir.resolve("authority.id");
        try {
            Files.createDirectories(dir);
            Optional<UUID> markerId = readMarker(marker);
            if (!Files.exists(database)) {
                if (markerId.isPresent())
                    return disabled("The progression database is missing but this world has an authority marker. "
                            + "Restore the matching backup of " + database + " (world and database must be restored together).");
                UUID authority = UUID.randomUUID();
                var store = ProgressionStore.open(database, rules, authority);
                writeMarker(marker, authority);
                LOG.info("Created a new progression store for this world at {}", database);
                return new AscensionRuntime(store, authority, null);
            }
            Optional<UUID> storeId = ProgressionStore.peekAuthority(database);
            if (storeId.isEmpty()) return disabled("The progression database has no authority record: " + database);
            if (markerId.isPresent() && !markerId.get().equals(storeId.get()))
                return disabled("The progression database belongs to a different world (authority mismatch): " + database);
            var options = ProgressionStore.Options.defaults().withMode(ProgressionStore.OpenMode.REQUIRE_EXISTING);
            var store = ProgressionStore.open(database, rules, storeId.get(), options);
            if (markerId.isEmpty()) {
                writeMarker(marker, storeId.get());
                LOG.warn("Authority marker was missing; adopted the store's authority {}", storeId.get());
            }
            return new AscensionRuntime(store, storeId.get(), null);
        } catch (StoreException exception) {
            LOG.error("Progression store refused to open ({}); progression changes are disabled", exception.code(), exception);
            return disabled(exception.code() + ": " + exception.getMessage());
        } catch (IOException | RuntimeException exception) {
            LOG.error("Progression store could not be prepared; progression changes are disabled", exception);
            return disabled("Unexpected error: " + exception.getMessage());
        }
    }

    private static AscensionRuntime disabled(String reason) {
        LOG.error("Progression DISABLED for this world: {}", reason);
        return new AscensionRuntime(null, null, reason);
    }

    private static Optional<UUID> readMarker(Path marker) throws IOException {
        if (!Files.exists(marker)) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(Files.readString(marker, StandardCharsets.UTF_8).trim()));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Authority marker is not a UUID: " + marker, exception);
        }
    }

    private static void writeMarker(Path marker, UUID authority) throws IOException {
        Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp");
        Files.writeString(temporary, authority + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public boolean enabled() { return store != null; }
    public String disabledReason() { return disabledReason; }
    public ProgressionStore store() { return store; }
    public UUID authority() { return authority; }
    SecureRandom newRandom() { return new SecureRandom(); }

    @Override public void close() {
        if (store != null) store.close();
    }
}
