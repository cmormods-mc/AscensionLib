package com.cobbleascend.store;

import com.cobbleascend.domain.v1.*;
import com.cobbleascend.store.StoreException.Code;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Canonical progression store (specification sections 4.2, 6.1 and 13): profiles, material wallets and an
 * operation log in one embedded SQLite file. Every mutating call is one transaction that also records its
 * result under the caller's operation ID, so a repeated request returns the first result and a crash leaves
 * either nothing or the complete committed change. Ownership and battle/trade locks are checked by the
 * application service before calling; the store enforces revisions, funds and identity.
 *
 * <p>All access is serialized through this instance. Use one instance per world.
 */
public final class ProgressionStore implements AutoCloseable {
    public static final int SCHEMA_VERSION = 2;

    public enum OpenMode { CREATE_IF_ABSENT, REQUIRE_EXISTING }

    public record Options(OpenMode mode, StoreConfig config, Clock clock, RandomGenerator random, Faults faults) {
        public Options {
            Objects.requireNonNull(mode);
            Objects.requireNonNull(config);
            Objects.requireNonNull(clock);
            Objects.requireNonNull(random);
            Objects.requireNonNull(faults);
        }

        public static Options defaults() {
            return new Options(OpenMode.CREATE_IF_ABSENT, StoreConfig.defaults(), Clock.systemUTC(),
                    new SecureRandom(), Faults.NONE);
        }

        public Options withMode(OpenMode value) { return new Options(value, config, clock, random, faults); }
        public Options withConfig(StoreConfig value) { return new Options(mode, value, clock, random, faults); }
        public Options withClock(Clock value) { return new Options(mode, config, value, random, faults); }
        public Options withRandom(RandomGenerator value) { return new Options(mode, config, clock, value, faults); }
        public Options withFaults(Faults value) { return new Options(mode, config, clock, random, value); }
    }

    private interface Work<T> { T run() throws SQLException; }

    /** What a fresh operation produced, before it is recorded. */
    private record Fresh(Kind kind, UUID playerId, UUID pokemonId, ProfileV1 profile, MaterialWallet wallet,
                         Map<MaterialId, Long> cost, int creditsSpent) {}

    private record Recorded(String hash, Kind kind, UUID pokemonId, String result) {}

    private final Path file;
    private final RankedRules rules;
    private final UUID authorityId;
    private final Options options;
    private final ProfileV1Codec codec;
    private final Connection db;
    private byte[] wildSecret;

    private ProgressionStore(Path file, RankedRules rules, UUID authorityId, Options options, Connection db) {
        this.file = file;
        this.rules = rules;
        this.authorityId = authorityId;
        this.options = options;
        this.codec = new ProfileV1Codec(rules);
        this.db = db;
    }

    public static ProgressionStore open(Path file, RankedRules rules, UUID authorityId) {
        return open(file, rules, authorityId, Options.defaults());
    }

    public static ProgressionStore open(Path file, RankedRules rules, UUID authorityId, Options options) {
        Objects.requireNonNull(rules);
        Objects.requireNonNull(authorityId);
        boolean existed = Files.exists(file);
        if (options.mode() == OpenMode.REQUIRE_EXISTING && !existed)
            throw new StoreException(Code.STORE_MISSING, "Progression store is missing: " + file);
        Connection connection = null;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            // Direct driver use: DriverManager's service lookup can miss a driver nested in a mod jar.
            connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file.toAbsolutePath(), new Properties());
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=FULL");
            }
            var store = new ProgressionStore(file, rules, authorityId, options, connection);
            store.initialize();
            return store;
        } catch (StoreException exception) {
            closeQuietly(connection);
            throw exception;
        } catch (SQLException | java.io.IOException exception) {
            closeQuietly(connection);
            throw new StoreException(Code.STORE_UNAVAILABLE, "Cannot open progression store", exception);
        }
    }

    /** Reads the authority ID recorded in an existing store without opening it for writing. */
    public static Optional<UUID> peekAuthority(Path file) {
        if (!Files.exists(file)) return Optional.empty();
        var config = new org.sqlite.SQLiteConfig();
        config.setReadOnly(true);
        try (var connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + file.toAbsolutePath(), config.toProperties());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT value FROM meta WHERE key = 'authority_id'")) {
            return rows.next() ? Optional.of(UUID.fromString(rows.getString(1))) : Optional.empty();
        } catch (SQLException | IllegalArgumentException exception) {
            throw new StoreException(Code.STORE_UNAVAILABLE, "Cannot read the store's authority", exception);
        }
    }

    /** Confirms the bundled SQLite driver and its native library load in this runtime; returns the engine version. */
    public static String driverVersion() {
        try (var connection = new org.sqlite.JDBC().connect("jdbc:sqlite::memory:", new Properties());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT sqlite_version()")) {
            rows.next();
            return rows.getString(1);
        } catch (SQLException | LinkageError exception) {
            throw new StoreException(Code.STORE_UNAVAILABLE, "SQLite driver is not usable in this runtime", exception);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) try { connection.close(); } catch (SQLException ignored) { }
    }

    private void initialize() throws SQLException {
        try (var statement = db.createStatement(); var check = statement.executeQuery("PRAGMA quick_check")) {
            if (!check.next() || !"ok".equals(check.getString(1)))
                throw new StoreException(Code.STORE_CORRUPT, "Progression store failed its integrity check");
        }
        db.setAutoCommit(false);
        try {
            try (var statement = db.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL) STRICT");
            }
            String version = meta("schema_version");
            if (version == null) {
                createSchema();
                putMeta("schema_version", Integer.toString(SCHEMA_VERSION));
                putMeta("authority_id", authorityId.toString());
                putMeta("catalog_version", Integer.toString(rules.catalogVersion()));
            } else {
                if (version.equals("1")) {
                    // Schema 2 only adds the fusions table: existing rows are untouched.
                    createFusionsTable();
                    try (var update = db.prepareStatement("UPDATE meta SET value = ? WHERE key = 'schema_version'")) {
                        update.setString(1, Integer.toString(SCHEMA_VERSION));
                        update.executeUpdate();
                    }
                } else if (!version.equals(Integer.toString(SCHEMA_VERSION)))
                    throw new StoreException(Code.STORE_SCHEMA_UNKNOWN, "Unsupported store schema " + version);
                if (!authorityId.toString().equals(meta("authority_id")))
                    throw new StoreException(Code.AUTHORITY_MISMATCH, "Store belongs to a different progression authority");
                if (!Integer.toString(rules.catalogVersion()).equals(meta("catalog_version")))
                    throw new StoreException(Code.CATALOG_MISMATCH, "Store content version requires an explicit migration");
            }
            if (meta("wild_secret") == null) {
                byte[] secret = new byte[32];
                new SecureRandom().nextBytes(secret);
                putMeta("wild_secret", HexFormat.of().formatHex(secret));
            }
            wildSecret = HexFormat.of().parseHex(meta("wild_secret"));
            db.commit();
        } catch (RuntimeException | SQLException exception) {
            db.rollback();
            throw exception;
        }
    }

    private void createFusionsTable() throws SQLException {
        try (var statement = db.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS fusions(
                        host_pokemon_id TEXT PRIMARY KEY,
                        donor_pokemon_id TEXT NOT NULL UNIQUE,
                        host_species TEXT NOT NULL,
                        donor_species TEXT NOT NULL,
                        host_unique TEXT NOT NULL,
                        donor_unique TEXT NOT NULL,
                        operation_id TEXT NOT NULL,
                        fused_at INTEGER NOT NULL) STRICT""");
        }
    }

    private void createSchema() throws SQLException {
        createFusionsTable();
        try (var statement = db.createStatement()) {
            statement.execute("""
                    CREATE TABLE profiles(
                        profile_id TEXT PRIMARY KEY,
                        pokemon_id TEXT NOT NULL UNIQUE,
                        revision INTEGER NOT NULL CHECK(revision >= 1),
                        projected_revision INTEGER NOT NULL DEFAULT 0,
                        body TEXT NOT NULL) STRICT""");
            statement.execute("""
                    CREATE TABLE wallets(
                        player_id TEXT PRIMARY KEY,
                        revision INTEGER NOT NULL CHECK(revision >= 1)) STRICT""");
            statement.execute("""
                    CREATE TABLE wallet_balances(
                        player_id TEXT NOT NULL REFERENCES wallets(player_id),
                        material_id TEXT NOT NULL,
                        amount INTEGER NOT NULL CHECK(amount >= 0),
                        PRIMARY KEY(player_id, material_id)) STRICT""");
            statement.execute("""
                    CREATE TABLE operations(
                        operation_id TEXT PRIMARY KEY,
                        request_hash TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        pokemon_id TEXT,
                        committed_at INTEGER NOT NULL,
                        result TEXT NOT NULL) STRICT""");
            statement.execute("""
                    CREATE TABLE milestone_awards(
                        profile_id TEXT NOT NULL REFERENCES profiles(profile_id),
                        milestone_level INTEGER NOT NULL CHECK(milestone_level BETWEEN 10 AND 100),
                        operation_id TEXT NOT NULL,
                        PRIMARY KEY(profile_id, milestone_level)) STRICT""");
            statement.execute("""
                    CREATE TABLE migrations(
                        profile_id TEXT PRIMARY KEY REFERENCES profiles(profile_id),
                        source_digest TEXT NOT NULL,
                        operation_id TEXT NOT NULL,
                        migrated_at INTEGER NOT NULL) STRICT""");
        }
    }

    private String meta(String key) throws SQLException {
        try (var statement = db.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
            statement.setString(1, key);
            try (var rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private void putMeta(String key, String value) throws SQLException {
        try (var statement = db.prepareStatement("INSERT INTO meta(key, value) VALUES(?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    public Path file() { return file; }
    public UUID authorityId() { return authorityId; }

    /**
     * Server-only key that makes wild Pokemon ratings unpredictable yet fixed per Pokemon. Never send it to a
     * client or log it. Losing the store loses it, which is why store and world are backed up together.
     */
    public byte[] wildSecret() { return wildSecret.clone(); }

    @Override public synchronized void close() {
        try { db.close(); } catch (SQLException exception) {
            throw new StoreException(Code.STORE_UNAVAILABLE, "Cannot close progression store", exception);
        }
    }

    // --- reads ----------------------------------------------------------------------------------------

    public synchronized Optional<ProfileV1> profile(UUID pokemonId) {
        return read(() -> loadProfile(pokemonId));
    }

    public synchronized MaterialWallet wallet(UUID playerId) {
        return read(() -> loadWallet(playerId));
    }

    public synchronized boolean hasOperation(UUID operationId) {
        return read(() -> lookupOperation(operationId) != null);
    }

    public synchronized Set<Integer> awardedMilestones(UUID profileId) {
        return read(() -> {
            var levels = new TreeSet<Integer>();
            try (var statement = db.prepareStatement("SELECT milestone_level FROM milestone_awards WHERE profile_id = ?")) {
                statement.setString(1, profileId.toString());
                try (var rows = statement.executeQuery()) { while (rows.next()) levels.add(rows.getInt(1)); }
            }
            return levels;
        });
    }

    /** Profiles committed at a revision the Cobblemon projection has not yet received (crash recovery). */
    public synchronized List<ProfileV1> projectionBacklog() {
        return read(() -> {
            var result = new ArrayList<ProfileV1>();
            try (var statement = db.prepareStatement(
                    "SELECT pokemon_id, body FROM profiles WHERE revision > projected_revision ORDER BY pokemon_id");
                 var rows = statement.executeQuery()) {
                while (rows.next()) result.add(decodeProfile(rows.getString(2), UUID.fromString(rows.getString(1))));
            }
            return result;
        });
    }

    public synchronized void markProjected(UUID profileId, long revision) {
        transaction(() -> {
            try (var statement = db.prepareStatement("""
                    UPDATE profiles SET projected_revision = ?
                    WHERE profile_id = ? AND ? <= revision AND ? > projected_revision""")) {
                statement.setLong(1, revision);
                statement.setString(2, profileId.toString());
                statement.setLong(3, revision);
                statement.setLong(4, revision);
                statement.executeUpdate();
            }
            return null;
        });
    }

    /** Runs a read and ends its implicit transaction so no snapshot stays open between calls. */
    private <T> T read(Work<T> work) {
        try {
            T result = work.run();
            db.commit();
            return result;
        } catch (RuntimeException exception) {
            rollbackQuietly();
            throw exception;
        } catch (SQLException exception) {
            rollbackQuietly();
            throw new StoreException(Code.STORE_UNAVAILABLE, "Progression store read failed", exception);
        }
    }

    // --- operations -----------------------------------------------------------------------------------

    /**
     * Registers a legitimately acquired Pokemon. The generator runs only when this operation has not already
     * committed, so a repeated capture callback resolves the existing profile instead of re-rolling it.
     */
    public synchronized Outcome acquire(UUID operationId, UUID pokemonId, Supplier<ProfileV1> generator) {
        return run(operationId, "ACQUIRE|" + pokemonId, () -> {
            if (loadProfile(pokemonId).isPresent())
                throw new StoreException(Code.DUPLICATE_POKEMON, "Pokemon already has a canonical profile");
            var profile = generator.get();
            if (!profile.pokemonId().equals(pokemonId))
                throw new IllegalArgumentException("Generated profile belongs to a different Pokemon");
            codec.encode(profile);
            insertProfile(profile);
            recordMilestones(profile.profileId(), profile.awardedMilestones(), operationId);
            return new Fresh(Kind.ACQUIRE, null, pokemonId, profile, null, Map.of(), 0);
        });
    }

    /**
     * Imports a stored schema-zero (or schema-1) profile once. Running it again for a Pokemon that already
     * migrated from the same source is a no-op that returns the existing profile.
     */
    public synchronized Outcome migrate(UUID operationId, UUID pokemonId, String encodedSource, int observedLevel) {
        String digest = sha256(encodedSource);
        return run(operationId, "MIGRATE|" + pokemonId + "|" + observedLevel + "|" + digest, () -> {
            var existing = loadProfile(pokemonId);
            if (existing.isPresent()) {
                if (!digest.equals(migrationDigest(existing.get().profileId())))
                    throw new StoreException(Code.DUPLICATE_POKEMON, "Pokemon already has a different canonical profile");
                return new Fresh(Kind.MIGRATE, null, pokemonId, existing.get(), null, Map.of(), 0);
            }
            var profile = new SchemaZeroMigration(rules).importStored(encodedSource, pokemonId, authorityId, observedLevel);
            insertProfile(profile);
            recordMilestones(profile.profileId(), profile.awardedMilestones(), operationId);
            try (var statement = db.prepareStatement(
                    "INSERT INTO migrations(profile_id, source_digest, operation_id, migrated_at) VALUES(?, ?, ?, ?)")) {
                statement.setString(1, profile.profileId().toString());
                statement.setString(2, digest);
                statement.setString(3, operationId.toString());
                statement.setLong(4, options.clock().millis());
                statement.executeUpdate();
            }
            return new Fresh(Kind.MIGRATE, null, pokemonId, profile, null, Map.of(), 0);
        });
    }

    /** Credits materials (rewards, admin grants). Idempotent per operation ID. */
    public synchronized Outcome grant(UUID operationId, UUID playerId, Map<MaterialId, Long> materials, String reason) {
        Objects.requireNonNull(reason);
        var canonical = new TreeMap<String, Long>();
        materials.forEach((id, amount) -> canonical.put(id.id(), amount));
        return run(operationId, "GRANT|" + playerId + "|" + canonical + "|" + reason, () -> {
            var before = loadWallet(playerId);
            var after = before.credit(materials);
            if (after == before) throw new CraftException(CraftException.Reason.INVALID_AMOUNT, "Nothing to grant");
            saveWallet(playerId, before, after);
            return new Fresh(Kind.GRANT, playerId, null, null, after, Map.of(), 0);
        });
    }

    /** Debits materials for a non-craft use (e.g. a Scouter). Idempotent per operation ID; refuses overdrafts. */
    public synchronized Outcome spend(UUID operationId, UUID playerId, Map<MaterialId, Long> materials, String reason) {
        Objects.requireNonNull(reason);
        var canonical = new TreeMap<String, Long>();
        materials.forEach((id, amount) -> canonical.put(id.id(), amount));
        return run(operationId, "SPEND|" + playerId + "|" + canonical + "|" + reason, () -> {
            var before = loadWallet(playerId);
            var after = before.debit(materials);
            if (after == before) throw new CraftException(CraftException.Reason.INVALID_AMOUNT, "Nothing to spend");
            options.faults().at(Faults.Point.AFTER_VALIDATION);
            saveWallet(playerId, before, after);
            return new Fresh(Kind.SPEND, playerId, null, null, after, materials, 0);
        });
    }

    /** Consumes the configured fragment threshold and credits one Catalyst. */
    public synchronized Outcome assembleCatalyst(UUID operationId, UUID playerId, long expectedWalletRevision) {
        return run(operationId, "ASSEMBLE|" + playerId + "|" + expectedWalletRevision, () -> {
            var before = loadWallet(playerId);
            if (before.revision() != expectedWalletRevision)
                throw new StoreException(Code.STALE_WALLET, "Wallet changed since it was shown");
            var after = before.assembleCatalyst(options.config().catalystFragments());
            options.faults().at(Faults.Point.AFTER_VALIDATION);
            saveWallet(playerId, before, after);
            return new Fresh(Kind.ASSEMBLE_CATALYST, playerId, null, null, after,
                    Map.of(MaterialId.UNIQUE_FRAGMENT, (long) options.config().catalystFragments()), 0);
        });
    }

    /** Executes one confirmed craft: revision checks, domain transition, debit, writes and result record. */
    public synchronized Outcome craft(CraftRequest request) {
        return run(request.operationId(), request.canonical(), () -> {
            var profile = loadProfile(request.pokemonId())
                    .orElseThrow(() -> new StoreException(Code.UNKNOWN_PROFILE, "No profile for that Pokemon"));
            if (profile.revision() != request.expectedProfileRevision())
                throw new StoreException(Code.STALE_PROFILE, "Profile changed since it was shown");
            MaterialWallet before = null;
            if (request.playerId() != null) {
                before = loadWallet(request.playerId());
                if (request.expectedWalletRevision() != CraftRequest.ANY_REVISION
                        && before.revision() != request.expectedWalletRevision())
                    throw new StoreException(Code.STALE_WALLET, "Wallet changed since it was shown");
            }
            var progression = new RankedProgression(rules);
            var random = options.random();
            ProfileV1 next;
            Map<MaterialId, Long> cost = Map.of();
            int credits = 0;
            switch (request.kind()) {
                case OBSERVE_LEVEL -> next = progression.observeLevel(profile, request.level()).profile();
                default -> {
                    var result = switch (request.kind()) {
                        case UPGRADE -> progression.upgrade(profile, request.target(), random);
                        case REFORGE -> progression.reforge(profile, request.target(), request.types(), random);
                        case REFINE -> progression.refine(profile, request.target(), random);
                        case PROMOTE -> progression.promote(profile, request.types(), random);
                        case AWARD_ATTUNEMENT -> progression.awardAttunement(profile, request.level());
                        case INSTALL_UNIQUE -> progression.installUnique(profile, request.target(), request.operationId());
                        case REPLACE_UNIQUE -> progression.replaceUnique(profile, request.target(), request.operationId());
                        default -> throw new IllegalStateException("Unreachable craft kind");
                    };
                    next = result.profile();
                    cost = result.cost();
                    credits = result.creditsSpent();
                }
            }
            MaterialWallet after = before == null ? null : before.debit(cost);
            options.faults().at(Faults.Point.AFTER_VALIDATION);
            if (next != profile) {
                updateProfile(next, profile.revision());
                var added = new TreeSet<>(next.awardedMilestones());
                added.removeAll(profile.awardedMilestones());
                recordMilestones(next.profileId(), added, request.operationId());
            }
            if (after != null && after != before) saveWallet(request.playerId(), before, after);
            return new Fresh(request.kind(), request.playerId(), request.pokemonId(), next, after, cost, credits);
        });
    }

    /** The fusion that made this Pokemon a Transcendent, if any. */
    public synchronized Optional<Fusion> fusion(UUID hostId) {
        return read(() -> loadFusion("host_pokemon_id", hostId));
    }

    /** The fusion that consumed this Pokemon as a donor, if any: the Pokemon must be gone, and the caller finishes removing it. */
    public synchronized Optional<Fusion> consumedBy(UUID donorId) {
        return read(() -> loadFusion("donor_pokemon_id", donorId));
    }

    /** Every Pokemon that is a Transcendent host or a consumed donor, in one read: a screen listing candidates asks this once, not once each. */
    public synchronized Set<UUID> fusedPokemon() {
        return read(() -> {
            var ids = new HashSet<UUID>();
            try (var statement = db.createStatement(); var rows = statement.executeQuery("SELECT host_pokemon_id, donor_pokemon_id FROM fusions")) {
                while (rows.next()) {
                    ids.add(UUID.fromString(rows.getString(1)));
                    ids.add(UUID.fromString(rows.getString(2)));
                }
            }
            return ids;
        });
    }

    private Optional<Fusion> loadFusion(String column, UUID id) throws SQLException {
        try (var statement = db.prepareStatement("SELECT host_pokemon_id, donor_pokemon_id, host_species, donor_species, host_unique,"
                + " donor_unique, operation_id, fused_at FROM fusions WHERE " + column + " = ?")) {
            statement.setString(1, id.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new Fusion(UUID.fromString(rows.getString(1)), UUID.fromString(rows.getString(2)), rows.getString(3),
                        rows.getString(4), rows.getString(5), rows.getString(6), UUID.fromString(rows.getString(7)), rows.getLong(8)));
            }
        }
    }

    /**
     * Executes one confirmed fusion in one transaction: both revisions and the wallet are checked, {@link FusionRules} decides, the
     * price is debited, the fusion is recorded and the host revision advances. The donor profile row is left in place (like a
     * released Pokemon); the application service removes the donor Pokemon after this commits, and a replay of the same operation
     * ID returns the first result so an interrupted removal can be finished.
     */
    public synchronized Outcome fuse(FuseRequest request) {
        return run(request.operationId(), request.canonical(), () -> {
            var host = loadProfile(request.hostId())
                    .orElseThrow(() -> new StoreException(Code.UNKNOWN_PROFILE, "No profile for the host"));
            var donor = loadProfile(request.donorId())
                    .orElseThrow(() -> new StoreException(Code.UNKNOWN_PROFILE, "No profile for the donor"));
            if (host.revision() != request.expectedHostRevision() || donor.revision() != request.expectedDonorRevision())
                throw new StoreException(Code.STALE_PROFILE, "A profile changed since it was shown");
            var before = loadWallet(request.playerId());
            if (request.expectedWalletRevision() != CraftRequest.ANY_REVISION && before.revision() != request.expectedWalletRevision())
                throw new StoreException(Code.STALE_WALLET, "Wallet changed since it was shown");
            boolean hostFused = loadFusion("host_pokemon_id", request.hostId()).isPresent() || loadFusion("donor_pokemon_id", request.hostId()).isPresent();
            boolean donorFused = loadFusion("host_pokemon_id", request.donorId()).isPresent() || loadFusion("donor_pokemon_id", request.donorId()).isPresent();
            FusionRules.check(host, hostFused, donor, donorFused, before);
            var cost = FusionRules.cost();
            var after = before.debit(cost);
            options.faults().at(Faults.Point.AFTER_VALIDATION);
            try (var statement = db.prepareStatement("INSERT INTO fusions(host_pokemon_id, donor_pokemon_id, host_species, donor_species,"
                    + " host_unique, donor_unique, operation_id, fused_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, request.hostId().toString());
                statement.setString(2, request.donorId().toString());
                statement.setString(3, request.hostSpecies());
                statement.setString(4, request.donorSpecies());
                statement.setString(5, host.unique().uniqueId());
                statement.setString(6, donor.unique().uniqueId());
                statement.setString(7, request.operationId().toString());
                statement.setLong(8, options.clock().millis());
                statement.executeUpdate();
            }
            var next = host.bumped();
            updateProfile(next, host.revision());
            saveWallet(request.playerId(), before, after);
            return new Fresh(Kind.FUSE, request.playerId(), request.hostId(), next, after, cost, 0);
        });
    }

    // --- transaction machinery ------------------------------------------------------------------------

    private Outcome run(UUID operationId, String canonicalRequest, Work<Fresh> work) {
        Objects.requireNonNull(operationId);
        String hash = sha256(canonicalRequest);
        return transaction(() -> {
            var prior = lookupOperation(operationId);
            if (prior != null) {
                if (!prior.hash().equals(hash))
                    throw new StoreException(Code.OPERATION_REUSED, "Operation ID was used for a different request");
                return decodeOutcome(operationId, prior).asReplay();
            }
            var fresh = work.run();
            String result = encodeOutcome(fresh);
            try (var statement = db.prepareStatement("""
                    INSERT INTO operations(operation_id, request_hash, kind, pokemon_id, committed_at, result)
                    VALUES(?, ?, ?, ?, ?, ?)""")) {
                statement.setString(1, operationId.toString());
                statement.setString(2, hash);
                statement.setString(3, fresh.kind().name());
                statement.setString(4, fresh.pokemonId() == null ? null : fresh.pokemonId().toString());
                statement.setLong(5, options.clock().millis());
                statement.setString(6, result);
                statement.executeUpdate();
            }
            return new Outcome(operationId, fresh.kind(), fresh.profile(), fresh.wallet(), fresh.cost(),
                    fresh.creditsSpent(), false);
        });
    }

    private <T> T transaction(Work<T> work) {
        T result;
        try {
            result = work.run();
            options.faults().at(Faults.Point.BEFORE_COMMIT);
            db.commit();
        } catch (RuntimeException | Error exception) {
            rollbackQuietly();
            throw exception;
        } catch (SQLException exception) {
            rollbackQuietly();
            throw new StoreException(Code.STORE_UNAVAILABLE, "Progression store write failed", exception);
        }
        options.faults().at(Faults.Point.AFTER_COMMIT);
        return result;
    }

    private void rollbackQuietly() {
        try { db.rollback(); } catch (SQLException ignored) { }
    }

    // --- row access -----------------------------------------------------------------------------------

    private Optional<ProfileV1> loadProfile(UUID pokemonId) throws SQLException {
        try (var statement = db.prepareStatement("SELECT body FROM profiles WHERE pokemon_id = ?")) {
            statement.setString(1, pokemonId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(decodeProfile(rows.getString(1), pokemonId)) : Optional.empty();
            }
        }
    }

    private ProfileV1 decodeProfile(String body, UUID pokemonId) {
        try {
            return codec.decode(body, pokemonId);
        } catch (RuntimeException exception) {
            throw new StoreException(Code.STORED_DATA_INVALID, "Stored profile failed validation (preserved)", exception);
        }
    }

    private void insertProfile(ProfileV1 profile) throws SQLException {
        try (var statement = db.prepareStatement(
                "INSERT INTO profiles(profile_id, pokemon_id, revision, body) VALUES(?, ?, ?, ?)")) {
            statement.setString(1, profile.profileId().toString());
            statement.setString(2, profile.pokemonId().toString());
            statement.setLong(3, profile.revision());
            statement.setString(4, codec.encode(profile));
            statement.executeUpdate();
        }
    }

    private void updateProfile(ProfileV1 next, long expectedRevision) throws SQLException {
        try (var statement = db.prepareStatement(
                "UPDATE profiles SET revision = ?, body = ? WHERE profile_id = ? AND revision = ?")) {
            statement.setLong(1, next.revision());
            statement.setString(2, codec.encode(next));
            statement.setString(3, next.profileId().toString());
            statement.setLong(4, expectedRevision);
            if (statement.executeUpdate() != 1)
                throw new StoreException(Code.STALE_PROFILE, "Profile changed during the operation");
        }
    }

    private void recordMilestones(UUID profileId, Collection<Integer> levels, UUID operationId) throws SQLException {
        for (int level : levels) {
            try (var statement = db.prepareStatement(
                    "INSERT INTO milestone_awards(profile_id, milestone_level, operation_id) VALUES(?, ?, ?)")) {
                statement.setString(1, profileId.toString());
                statement.setInt(2, level);
                statement.setString(3, operationId.toString());
                statement.executeUpdate();
            }
        }
    }

    private String migrationDigest(UUID profileId) throws SQLException {
        try (var statement = db.prepareStatement("SELECT source_digest FROM migrations WHERE profile_id = ?")) {
            statement.setString(1, profileId.toString());
            try (var rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private MaterialWallet loadWallet(UUID playerId) throws SQLException {
        long revision;
        try (var statement = db.prepareStatement("SELECT revision FROM wallets WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return MaterialWallet.EMPTY;
                revision = rows.getLong(1);
            }
        }
        var balances = new EnumMap<MaterialId, Long>(MaterialId.class);
        try (var statement = db.prepareStatement("SELECT material_id, amount FROM wallet_balances WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    try {
                        balances.put(MaterialId.fromId(rows.getString(1)), rows.getLong(2));
                    } catch (IllegalArgumentException exception) {
                        throw new StoreException(Code.STORED_DATA_INVALID, "Stored wallet has an unknown material", exception);
                    }
                }
            }
        }
        return new MaterialWallet(revision, balances);
    }

    private void saveWallet(UUID playerId, MaterialWallet before, MaterialWallet after) throws SQLException {
        if (before.revision() == 0) {
            try (var statement = db.prepareStatement("INSERT INTO wallets(player_id, revision) VALUES(?, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, after.revision());
                statement.executeUpdate();
            }
        } else {
            try (var statement = db.prepareStatement("UPDATE wallets SET revision = ? WHERE player_id = ? AND revision = ?")) {
                statement.setLong(1, after.revision());
                statement.setString(2, playerId.toString());
                statement.setLong(3, before.revision());
                if (statement.executeUpdate() != 1)
                    throw new StoreException(Code.STALE_WALLET, "Wallet changed during the operation");
            }
        }
        for (var id : MaterialId.values()) {
            try (var statement = db.prepareStatement("""
                    INSERT INTO wallet_balances(player_id, material_id, amount) VALUES(?, ?, ?)
                    ON CONFLICT(player_id, material_id) DO UPDATE SET amount = excluded.amount""")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, id.id());
                statement.setLong(3, after.balance(id));
                statement.executeUpdate();
            }
        }
    }

    private Recorded lookupOperation(UUID operationId) throws SQLException {
        try (var statement = db.prepareStatement(
                "SELECT request_hash, kind, pokemon_id, result FROM operations WHERE operation_id = ?")) {
            statement.setString(1, operationId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                String pokemon = rows.getString(3);
                return new Recorded(rows.getString(1), Kind.valueOf(rows.getString(2)),
                        pokemon == null ? null : UUID.fromString(pokemon), rows.getString(4));
            }
        }
    }

    // --- result encoding ------------------------------------------------------------------------------

    private String encodeOutcome(Fresh fresh) {
        var root = new JsonObject();
        root.addProperty("kind", fresh.kind().name());
        if (fresh.profile() != null) root.add("profile", JsonParser.parseString(codec.encode(fresh.profile())));
        if (fresh.wallet() != null) {
            var wallet = new JsonObject();
            wallet.addProperty("revision", fresh.wallet().revision());
            var balances = new JsonObject();
            for (var id : MaterialId.values()) balances.addProperty(id.id(), fresh.wallet().balance(id));
            wallet.add("balances", balances);
            root.add("wallet", wallet);
        }
        var cost = new JsonObject();
        new TreeMap<String, Long>(toIds(fresh.cost())).forEach(cost::addProperty);
        root.add("cost", cost);
        root.addProperty("creditsSpent", fresh.creditsSpent());
        return root.toString();
    }

    private static Map<String, Long> toIds(Map<MaterialId, Long> amounts) {
        var result = new HashMap<String, Long>();
        amounts.forEach((id, amount) -> result.put(id.id(), amount));
        return result;
    }

    private Outcome decodeOutcome(UUID operationId, Recorded recorded) {
        try {
            var root = JsonParser.parseString(recorded.result()).getAsJsonObject();
            ProfileV1 profile = null;
            if (root.has("profile")) profile = codec.decode(root.get("profile").toString(), recorded.pokemonId());
            MaterialWallet wallet = null;
            if (root.has("wallet")) {
                var object = root.getAsJsonObject("wallet");
                var balances = new EnumMap<MaterialId, Long>(MaterialId.class);
                object.getAsJsonObject("balances").entrySet()
                        .forEach(e -> balances.put(MaterialId.fromId(e.getKey()), e.getValue().getAsLong()));
                wallet = new MaterialWallet(object.get("revision").getAsLong(), balances);
            }
            var cost = new EnumMap<MaterialId, Long>(MaterialId.class);
            root.getAsJsonObject("cost").entrySet()
                    .forEach(e -> cost.put(MaterialId.fromId(e.getKey()), e.getValue().getAsLong()));
            return new Outcome(operationId, recorded.kind(), profile, wallet, cost,
                    root.get("creditsSpent").getAsInt(), false);
        } catch (RuntimeException exception) {
            throw new StoreException(Code.STORED_DATA_INVALID, "Recorded operation result is unreadable (preserved)", exception);
        }
    }

    private static String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
