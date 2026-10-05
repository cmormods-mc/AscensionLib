package com.cobbleascend.store;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A confirmed craft (specification section 6.1 step 4): the operation UUID plus the revisions the player saw.
 * Server-driven operations may pass {@link #ANY_REVISION} for the wallet. Ownership of the Pokemon and of the
 * wallet is verified by the application service before a request is built; the store does not know owners.
 */
public record CraftRequest(UUID operationId, Kind kind, UUID playerId, UUID pokemonId, String target, int level,
                           List<String> types, long expectedProfileRevision, long expectedWalletRevision) {
    public static final long ANY_REVISION = -1;

    public CraftRequest {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(pokemonId);
        Objects.requireNonNull(target);
        types = List.copyOf(types);
        switch (kind) {
            case UPGRADE, REFORGE, REFINE, PROMOTE, INSTALL_UNIQUE, REPLACE_UNIQUE -> Objects.requireNonNull(playerId);
            case OBSERVE_LEVEL, AWARD_ATTUNEMENT -> { }
            default -> throw new IllegalArgumentException("Not a craft request kind: " + kind);
        }
        if (expectedProfileRevision < 1) throw new IllegalArgumentException("Profile revision is required");
        if (expectedWalletRevision < ANY_REVISION) throw new IllegalArgumentException("Invalid wallet revision");
    }

    public static CraftRequest upgrade(UUID op, UUID player, UUID pokemon, String slotId, long profileRevision, long walletRevision) {
        return new CraftRequest(op, Kind.UPGRADE, player, pokemon, slotId, 0, List.of(), profileRevision, walletRevision);
    }

    public static CraftRequest reforge(UUID op, UUID player, UUID pokemon, String slotId, List<String> types, long profileRevision, long walletRevision) {
        return new CraftRequest(op, Kind.REFORGE, player, pokemon, slotId, 0, types, profileRevision, walletRevision);
    }

    public static CraftRequest refine(UUID op, UUID player, UUID pokemon, String slotId, long profileRevision, long walletRevision) {
        return new CraftRequest(op, Kind.REFINE, player, pokemon, slotId, 0, List.of(), profileRevision, walletRevision);
    }

    public static CraftRequest promote(UUID op, UUID player, UUID pokemon, List<String> types, long profileRevision, long walletRevision) {
        return new CraftRequest(op, Kind.PROMOTE, player, pokemon, "", 0, types, profileRevision, walletRevision);
    }

    public static CraftRequest installUnique(UUID op, UUID player, UUID pokemon, String uniqueId, long profileRevision, long walletRevision) {
        return new CraftRequest(op, Kind.INSTALL_UNIQUE, player, pokemon, uniqueId, 0, List.of(), profileRevision, walletRevision);
    }

    public static CraftRequest replaceUnique(UUID op, UUID player, UUID pokemon, String uniqueId, long profileRevision, long walletRevision) {
        return new CraftRequest(op, Kind.REPLACE_UNIQUE, player, pokemon, uniqueId, 0, List.of(), profileRevision, walletRevision);
    }

    /** Server-driven: no wallet or player involved. */
    public static CraftRequest observeLevel(UUID op, UUID pokemon, int level, long profileRevision) {
        return new CraftRequest(op, Kind.OBSERVE_LEVEL, null, pokemon, "", level, List.of(), profileRevision, ANY_REVISION);
    }

    /** Server-driven: no wallet or player involved; {@code points} travels in the level field. */
    public static CraftRequest awardAttunement(UUID op, UUID pokemon, int points, long profileRevision) {
        return new CraftRequest(op, Kind.AWARD_ATTUNEMENT, null, pokemon, "", points, List.of(), profileRevision, ANY_REVISION);
    }

    /** Canonical form hashed to detect an operation ID reused with different parameters. */
    String canonical() {
        return String.join("|", kind.name(), String.valueOf(playerId), pokemonId.toString(), target,
                Integer.toString(level), String.join(",", types), Long.toString(expectedProfileRevision),
                Long.toString(expectedWalletRevision));
    }
}
