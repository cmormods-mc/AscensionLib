package com.cobbleascend.domain.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What a scouted enemy shows: its rarity, modifiers and Unique as display text and numbers. Built only after a
 * reveal, so an unscouted enemy has nothing to leak. Never carries IVs, EVs or moves.
 *
 * @param uniqueName the Unique's display name, or empty when the enemy has none
 */
public record ScoutView(String rarityId, String uniqueName, List<SlotLine> slots) {
    /** One modifier row: {@code category} is {@code prefix} or {@code suffix}. */
    public record SlotLine(String category, String name, int rank, int rolledValue) {}

    public ScoutView {
        Objects.requireNonNull(rarityId);
        Objects.requireNonNull(uniqueName);
        slots = List.copyOf(slots);
    }

    public static ScoutView of(CombatSnapshot snapshot, RankedRules rules) {
        var lines = new ArrayList<SlotLine>();
        for (var slot : snapshot.slots()) {
            lines.add(new SlotLine(slot.category().id(), rules.affix(slot.affixId()).base().name(), slot.rank(),
                    slot.rolledValue()));
        }
        String unique = snapshot.uniqueId() == null ? ""
                : rules.unique(snapshot.uniqueId()).map(UniqueDefinition::name).orElse(snapshot.uniqueId());
        return new ScoutView(snapshot.rarity().id(), unique, lines);
    }
}
