package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Rarity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * Strict schema-1 projection boundary. Unknown fields, wrong types, fractional integers and failed
 * validation all reject the input; callers must preserve the source rather than repair it.
 */
public final class ProfileV1Codec {
    static final int MAX_LENGTH = 16384;
    private final RankedRules rules;

    public ProfileV1Codec(RankedRules rules) { this.rules = Objects.requireNonNull(rules); }

    public String encode(ProfileV1 profile) {
        rules.validate(profile);
        var root = new JsonObject();
        root.addProperty("schemaVersion", profile.schemaVersion());
        root.addProperty("profileId", profile.profileId().toString());
        root.addProperty("pokemonId", profile.pokemonId().toString());
        root.addProperty("authorityId", profile.authorityId().toString());
        root.addProperty("revision", profile.revision());
        root.addProperty("rarity", profile.rarity().id());
        root.addProperty("initialRarity", profile.initialRarity().id());
        var origin = new JsonObject();
        origin.addProperty("kind", profile.origin().kind());
        if (profile.origin().acquisitionId() != null)
            origin.addProperty("acquisitionId", profile.origin().acquisitionId().toString());
        root.add("origin", origin);
        root.addProperty("catalogVersion", profile.catalogVersion());
        root.addProperty("attunement", profile.attunement());
        root.addProperty("highestLevelObserved", profile.highestLevelObserved());
        var milestones = new JsonArray();
        profile.awardedMilestones().forEach(milestones::add);
        root.add("awardedMilestones", milestones);
        root.addProperty("spentUpgradeCredits", profile.spentUpgradeCredits());
        var slots = new JsonArray();
        for (var slot : profile.ordinarySlots()) {
            var object = new JsonObject();
            object.addProperty("slotId", slot.slotId());
            object.addProperty("category", slot.category().id());
            object.addProperty("rank", slot.rank());
            object.addProperty("affixId", slot.affixId());
            var parameters = new JsonObject();
            new TreeMap<>(slot.parameters()).forEach(parameters::addProperty);
            object.add("parameters", parameters);
            object.addProperty("rolledValue", slot.rolledValue());
            object.addProperty("definitionVersion", slot.definitionVersion());
            slots.add(object);
        }
        root.add("ordinarySlots", slots);
        if (profile.unique() != null) {
            var unique = new JsonObject();
            unique.addProperty("uniqueId", profile.unique().uniqueId());
            unique.addProperty("definitionVersion", profile.unique().definitionVersion());
            unique.addProperty("installedOperationId", profile.unique().installedOperationId().toString());
            root.add("unique", unique);
        }
        return root.toString();
    }

    public ProfileV1 decode(String encoded, UUID expectedPokemon) {
        if (encoded.length() > MAX_LENGTH) throw new IllegalArgumentException("Profile exceeds size limit");
        var root = Json.parseObject(encoded);
        Json.keys(root, Set.of("schemaVersion", "profileId", "pokemonId", "authorityId", "revision", "rarity",
                "initialRarity", "origin", "catalogVersion", "attunement", "highestLevelObserved",
                "awardedMilestones", "spentUpgradeCredits", "ordinarySlots"), Set.of("unique"));
        if (Json.integer(root.get("schemaVersion")) != ProfileV1.SCHEMA)
            throw new IllegalArgumentException("Unsupported profile schema");
        var originObject = Json.object(root.get("origin"));
        Json.keys(originObject, Set.of("kind"), Set.of("acquisitionId"));
        var origin = new Origin(Json.string(originObject.get("kind")),
                originObject.has("acquisitionId") ? Json.uuid(originObject.get("acquisitionId")) : null);
        var milestones = new ArrayList<Integer>();
        for (var element : Json.array(root.get("awardedMilestones"))) milestones.add(Json.integer(element));
        if (new HashSet<>(milestones).size() != milestones.size())
            throw new IllegalArgumentException("Duplicate awarded milestone");
        var slots = new ArrayList<OrdinarySlot>();
        for (var element : Json.array(root.get("ordinarySlots"))) {
            var object = Json.object(element);
            Json.keys(object, Set.of("slotId", "category", "rank", "affixId", "parameters", "rolledValue",
                    "definitionVersion"), Set.of());
            var parameters = new TreeMap<String, String>();
            Json.object(object.get("parameters")).entrySet()
                    .forEach(e -> parameters.put(e.getKey(), Json.string(e.getValue())));
            slots.add(new OrdinarySlot(Json.string(object.get("slotId")),
                    Category.fromId(Json.string(object.get("category"))), Json.integer(object.get("rank")),
                    Json.string(object.get("affixId")), parameters, Json.integer(object.get("rolledValue")),
                    Json.integer(object.get("definitionVersion"))));
        }
        UniqueInstance unique = null;
        if (root.has("unique")) {
            var object = Json.object(root.get("unique"));
            Json.keys(object, Set.of("uniqueId", "definitionVersion", "installedOperationId"), Set.of());
            unique = new UniqueInstance(Json.string(object.get("uniqueId")),
                    Json.integer(object.get("definitionVersion")), Json.uuid(object.get("installedOperationId")));
        }
        var profile = new ProfileV1(ProfileV1.SCHEMA, Json.uuid(root.get("profileId")),
                Json.uuid(root.get("pokemonId")), Json.uuid(root.get("authorityId")),
                Json.longValue(root.get("revision")), Rarity.fromId(Json.string(root.get("rarity"))),
                Rarity.fromId(Json.string(root.get("initialRarity"))), origin,
                Json.integer(root.get("catalogVersion")), Json.integer(root.get("attunement")),
                Json.integer(root.get("highestLevelObserved")), new HashSet<>(milestones),
                Json.integer(root.get("spentUpgradeCredits")), slots, unique);
        if (!profile.pokemonId().equals(expectedPokemon))
            throw new IllegalArgumentException("Profile belongs to a different Pokemon");
        rules.validate(profile);
        return profile;
    }
}
