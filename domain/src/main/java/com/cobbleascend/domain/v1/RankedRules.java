package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.AffixDefinition;
import com.cobbleascend.domain.Rarity;
import com.cobbleascend.domain.Rules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Ranked catalog layered over the prototype {@link Rules} (rarity tiers, promotions, craft costs, caps).
 * The catalog is validated completely at construction; a release definition must support all five ranks.
 */
public final class RankedRules {
    private final Rules base;
    private final int catalogVersion;
    private final Map<String, RankedAffix> affixes;
    private final Map<String, UniqueDefinition> uniques;

    public RankedRules(Rules base, int catalogVersion, Map<String, List<RankBand>> bands,
                       List<UniqueDefinition> uniques) {
        this.base = Objects.requireNonNull(base);
        if (catalogVersion < 1) throw new IllegalArgumentException("Invalid catalog version");
        this.catalogVersion = catalogVersion;
        var affixMap = new LinkedHashMap<String, RankedAffix>();
        for (var definition : base.affixes()) {
            var affixBands = bands.get(definition.id());
            if (affixBands == null) throw new IllegalArgumentException("Missing rank bands: " + definition.id());
            affixMap.put(definition.id(), new RankedAffix(definition, catalogVersion, affixBands));
        }
        for (String id : bands.keySet()) {
            if (!affixMap.containsKey(id)) throw new IllegalArgumentException("Rank bands for unknown affix: " + id);
        }
        this.affixes = Collections.unmodifiableMap(affixMap);
        var uniqueMap = new LinkedHashMap<String, UniqueDefinition>();
        for (var unique : uniques) {
            if (unique.definitionVersion() != catalogVersion)
                throw new IllegalArgumentException("Unique definition version must match the catalog");
            if (uniqueMap.put(unique.id(), unique) != null) throw new IllegalArgumentException("Duplicate unique");
        }
        this.uniques = Collections.unmodifiableMap(uniqueMap);
    }

    public static RankedRules defaults() {
        return parse(Rules.defaults(), resource("ranked-catalog.json"));
    }

    public static RankedRules parse(Rules base, JsonObject catalog) {
        Json.keys(catalog, Set.of("schemaVersion", "catalogVersion", "ranks", "uniques"),
                Set.of("status", "bandRule"));
        if (Json.integer(catalog.get("schemaVersion")) != 1) throw new IllegalArgumentException("Unknown catalog schema");
        int version = Json.integer(catalog.get("catalogVersion"));
        var bands = new LinkedHashMap<String, List<RankBand>>();
        for (var entry : Json.object(catalog.get("ranks")).entrySet()) {
            var list = new ArrayList<RankBand>();
            for (var pair : Json.array(entry.getValue())) {
                var range = Json.array(pair);
                if (range.size() != 2) throw new IllegalArgumentException("A band is [min, max]");
                list.add(new RankBand(Json.integer(range.get(0)), Json.integer(range.get(1))));
            }
            bands.put(entry.getKey(), list);
        }
        var uniques = new ArrayList<UniqueDefinition>();
        for (var element : Json.array(catalog.get("uniques"))) {
            var unique = Json.object(element);
            Json.keys(unique, Set.of("id", "name"), Set.of("benefit", "drawback"));
            uniques.add(new UniqueDefinition(Json.string(unique.get("id")), Json.string(unique.get("name")),
                    unique.has("benefit") ? Json.string(unique.get("benefit")) : "",
                    unique.has("drawback") ? Json.string(unique.get("drawback")) : "", version));
        }
        return new RankedRules(base, version, bands, uniques);
    }

    private static JsonObject resource(String name) {
        var stream = RankedRules.class.getResourceAsStream("/cobbleascend/" + name);
        if (stream == null) throw new IllegalStateException("Missing rules resource: " + name);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read rules resource", exception);
        }
    }

    public Rules base() { return base; }
    public int catalogVersion() { return catalogVersion; }
    public Collection<RankedAffix> affixes() { return affixes.values(); }
    public Collection<UniqueDefinition> uniques() { return uniques.values(); }

    public RankedAffix affix(String id) {
        var result = affixes.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown affix: " + id);
        return result;
    }

    public Optional<UniqueDefinition> unique(String id) { return Optional.ofNullable(uniques.get(id)); }

    public int slotCount(Rarity rarity, Category category) {
        var tier = base.tier(rarity);
        return category == Category.PREFIX ? tier.prefixSlots() : tier.suffixSlots();
    }

    /** Full validation of a profile against this catalog. Throws IllegalArgumentException on any violation. */
    public void validate(ProfileV1 profile) {
        if (profile.catalogVersion() != catalogVersion)
            throw new IllegalArgumentException("Profile content version requires an explicit migration");
        validateSlots(profile.rarity(), profile.ordinarySlots());
        validateUnique(profile.unique() == null ? null : profile.unique().uniqueId(),
                profile.unique() == null ? 0 : profile.unique().definitionVersion());
    }

    /** Full validation of a frozen combat snapshot (player or enemy) against this catalog. */
    public void validate(CombatSnapshot snapshot) {
        if (snapshot.catalogVersion() != catalogVersion)
            throw new IllegalArgumentException("Snapshot content version does not match the active catalog");
        validateSlots(snapshot.rarity(), snapshot.slots());
        int invested = snapshot.slots().stream().mapToInt(s -> s.rank() - 1).sum();
        if (invested > Milestones.MAX_LEVEL / Milestones.STEP)
            throw new IllegalArgumentException("Slot ranks exceed the ten available milestone credits");
        validateUnique(snapshot.uniqueId(), snapshot.uniqueId() == null ? 0 : catalogVersion);
        if (snapshot.transcendent() != null) {
            for (var id : snapshot.transcendent().uniqueIds()) validateUnique(id, catalogVersion);
        }
    }

    private void validateSlots(Rarity rarity, Collection<OrdinarySlot> slots) {
        var families = new HashSet<String>();
        var counts = new EnumMap<Category, Integer>(Category.class);
        for (var slot : slots) {
            var affix = affix(slot.affixId());
            if (affix.category() != slot.category())
                throw new IllegalArgumentException("Affix does not belong to the slot category: " + slot.slotId());
            if (slot.definitionVersion() != affix.definitionVersion())
                throw new IllegalArgumentException("Slot definition version is stale: " + slot.slotId());
            if (!families.add(affix.family())) throw new IllegalArgumentException("Duplicate family");
            if (!affix.band(slot.rank()).contains(slot.rolledValue()))
                throw new IllegalArgumentException("Roll outside the band for rank " + slot.rank());
            checkParameters(affix.base(), slot);
            counts.merge(slot.category(), 1, Integer::sum);
        }
        for (var category : Category.values()) {
            if (counts.getOrDefault(category, 0) != slotCount(rarity, category))
                throw new IllegalArgumentException("Profile slots do not match rarity");
        }
    }

    private void validateUnique(String uniqueId, int version) {
        if (uniqueId == null) return;
        var definition = uniques.get(uniqueId);
        if (definition == null) throw new IllegalArgumentException("Unknown unique: " + uniqueId);
        if (definition.definitionVersion() != version)
            throw new IllegalArgumentException("Unique definition version is stale");
    }

    private static void checkParameters(AffixDefinition definition, OrdinarySlot slot) {
        if (definition.parameter() == null) {
            if (!slot.parameters().isEmpty()) throw new IllegalArgumentException("Unexpected affix parameters");
        } else if (!slot.parameters().keySet().equals(Set.of("type")) || !Rules.TYPES.contains(slot.type())) {
            throw new IllegalArgumentException("Invalid typed affix parameter");
        }
    }
}
