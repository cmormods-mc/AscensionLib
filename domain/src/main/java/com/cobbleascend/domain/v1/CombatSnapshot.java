package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Rarity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * The immutable, frozen description of one combatant's rarity and modifiers that a battle adapter consumes
 * (specification section 3: "immutable battle snapshot"). Players' snapshots come from their stored profile;
 * enemies' come from {@link EnemyGenerator}. The battle code never needs to know which it is, and a snapshot is
 * never written back: an enemy snapshot is not a profile and cannot be captured, traded or rewarded.
 */
public record CombatSnapshot(Source source, String subjectId, Rarity rarity, int catalogVersion,
                             List<OrdinarySlot> slots, String uniqueId) {
    public enum Source { PLAYER, ENEMY }

    public CombatSnapshot {
        Objects.requireNonNull(source);
        Objects.requireNonNull(subjectId);
        Objects.requireNonNull(rarity);
        slots = List.copyOf(slots);
        if (subjectId.isBlank() || subjectId.length() > 160) throw new IllegalArgumentException("Invalid subject ID");
        if (catalogVersion < 1) throw new IllegalArgumentException("Invalid catalog version");
        if (slots.size() > ProfileV1.MAX_SLOTS) throw new IllegalArgumentException("Too many slots");
        if (uniqueId != null && !uniqueId.matches("[a-z][a-z0-9_]{0,63}"))
            throw new IllegalArgumentException("Invalid unique ID");
    }

    /** A player's combatant, frozen from the committed profile. Unique tuning is looked up by ID at battle time. */
    public static CombatSnapshot ofProfile(ProfileV1 profile) {
        return new CombatSnapshot(Source.PLAYER, profile.pokemonId().toString(), profile.rarity(),
                profile.catalogVersion(), profile.ordinarySlots(),
                profile.unique() == null ? null : profile.unique().uniqueId());
    }

    /** Stable fingerprint, so a battle can prove its modifiers stayed frozen from start to finish. */
    public String contentHash() {
        var text = new StringJoiner("\n");
        text.add(source + "|" + subjectId + "|" + rarity.id() + "|" + catalogVersion + "|" + uniqueId);
        for (var slot : slots) {
            text.add(slot.slotId() + "|" + slot.rank() + "|" + slot.affixId() + "|"
                    + new TreeMap<>(slot.parameters()) + "|" + slot.rolledValue());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
