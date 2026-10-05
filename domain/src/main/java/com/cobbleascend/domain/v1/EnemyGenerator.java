package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Rarity;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Builds an enemy's rarity and modifiers from an encounter declaration. The result depends only on
 * (catalog version, encounter ID, enemy index, spec, species types), so a reconnect, a retry or a shared raid
 * always sees the same enemy, and generating enemies in any order gives identical results. Enemies use the same
 * slot counts, affix weights and rank bands as players; the tier only chooses the rarity table and how many
 * rank credits are spent. Nothing here is persisted or becomes a profile.
 */
public final class EnemyGenerator {
    private final RankedRules rules;
    private final RankedProgression progression;

    public EnemyGenerator(RankedRules rules) {
        this.rules = Objects.requireNonNull(rules);
        this.progression = new RankedProgression(rules);
    }

    public CombatSnapshot generate(String encounterId, int enemyIndex, EnemySpec spec, List<String> speciesTypes) {
        if (encounterId == null || encounterId.isBlank() || encounterId.length() > 128)
            throw new IllegalArgumentException("Invalid encounter ID");
        if (enemyIndex < 0) throw new IllegalArgumentException("Enemy index must not be negative");
        Objects.requireNonNull(spec);
        if (speciesTypes.isEmpty() || !com.cobbleascend.domain.Rules.TYPES.containsAll(speciesTypes))
            throw new IllegalArgumentException("Species types must be one or more standard types");
        if (spec.uniqueId() != null && rules.unique(spec.uniqueId()).isEmpty())
            throw new IllegalArgumentException("Unknown Unique: " + spec.uniqueId());

        var random = new Random(seed(encounterId, enemyIndex));
        Rarity rarity = spec.tier().rollRarity(random);
        var slots = new ArrayList<>(progression.rollInitialSlots(rarity, speciesTypes, random));
        spendRankCredits(slots, spec.tier().rankCredits(), random);
        var snapshot = new CombatSnapshot(CombatSnapshot.Source.ENEMY, encounterId + "#" + enemyIndex, rarity,
                rules.catalogVersion(), slots, spec.uniqueId());
        rules.validate(snapshot);
        return snapshot;
    }

    /** Spends credits one at a time on random slots below rank V; credits with no legal slot are unused. */
    private void spendRankCredits(List<OrdinarySlot> slots, int credits, Random random) {
        for (int remaining = credits; remaining > 0; remaining--) {
            var upgradable = new ArrayList<Integer>();
            for (int i = 0; i < slots.size(); i++) if (slots.get(i).rank() < RankedAffix.RANKS) upgradable.add(i);
            if (upgradable.isEmpty()) return;
            int index = upgradable.get(random.nextInt(upgradable.size()));
            var slot = slots.get(index);
            int rank = slot.rank() + 1;
            slots.set(index, new OrdinarySlot(slot.slotId(), slot.category(), rank, slot.affixId(), slot.parameters(),
                    rules.affix(slot.affixId()).band(rank).roll(random), slot.definitionVersion()));
        }
    }

    private long seed(String encounterId, int enemyIndex) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(
                    ("cobbleascend:enemy:v1|" + rules.catalogVersion() + "|" + encounterId + "|" + enemyIndex)
                            .getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
