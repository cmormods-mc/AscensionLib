package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.AffixRoll;
import com.cobbleascend.domain.Profile;
import com.cobbleascend.domain.ProfileCodec;
import java.util.*;

/**
 * Schema-zero to schema-1 import (specification section 13). Preserves identities, rarity, initial rarity,
 * origin and every rolled value. Slot IDs follow the existing list order per category, all at rank I;
 * reached milestones are granted once; the Unique starts absent. Invalid source data is rejected, never repaired.
 */
public final class SchemaZeroMigration {
    private final RankedRules rules;

    public SchemaZeroMigration(RankedRules rules) { this.rules = Objects.requireNonNull(rules); }

    public ProfileV1 migrate(Profile legacy, UUID authorityId, int observedLevel) {
        if (observedLevel < 1) throw new IllegalArgumentException("Level must be at least 1");
        var slots = new ArrayList<OrdinarySlot>();
        var counts = new EnumMap<Category, Integer>(Category.class);
        for (AffixRoll roll : legacy.affixes()) {
            var affix = rules.affix(roll.id());
            int index = counts.merge(affix.category(), 1, Integer::sum) - 1;
            slots.add(new OrdinarySlot(OrdinarySlot.slotId(affix.category(), index), affix.category(), 1,
                    roll.id(), roll.type() == null ? Map.of() : Map.of("type", roll.type()), roll.value(),
                    affix.definitionVersion()));
        }
        var profile = new ProfileV1(ProfileV1.SCHEMA, legacy.profileId(), legacy.pokemonId(), authorityId,
                Math.addExact(legacy.revision(), 1), legacy.rarity(), legacy.initialRarity(),
                Origin.of(legacy.origin()), rules.catalogVersion(), legacy.attunement(),
                Math.min(observedLevel, Milestones.MAX_LEVEL), Milestones.reachedThrough(observedLevel), 0,
                slots, null);
        rules.validate(profile);
        return profile;
    }

    /**
     * Reads either stored form. A schema-1 string is decoded unchanged, so re-running the import over its own
     * output is a no-op; a schema-zero string is validated by the prototype codec, then migrated.
     */
    public ProfileV1 importStored(String encoded, UUID expectedPokemon, UUID authorityId, int observedLevel) {
        if (encoded.length() > ProfileV1Codec.MAX_LENGTH) throw new IllegalArgumentException("Profile exceeds size limit");
        var root = Json.parseObject(encoded);
        if (!root.has("schemaVersion")) throw new IllegalArgumentException("Missing field: schemaVersion");
        return switch (Json.integer(root.get("schemaVersion"))) {
            case ProfileV1.SCHEMA -> new ProfileV1Codec(rules).decode(encoded, expectedPokemon);
            case Profile.SCHEMA -> migrate(new ProfileCodec(rules.base()).decode(encoded, expectedPokemon),
                    authorityId, observedLevel);
            default -> throw new IllegalArgumentException("Unsupported profile schema");
        };
    }
}
