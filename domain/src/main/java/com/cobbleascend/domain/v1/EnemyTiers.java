package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Rarity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validated registry of enemy tiers loaded from {@code design/enemy-tiers.json} (provisional values). */
public final class EnemyTiers {
    private final Map<String, EnemyTier> tiers;

    public EnemyTiers(List<EnemyTier> list) {
        var map = new LinkedHashMap<String, EnemyTier>();
        for (var tier : list) if (map.put(tier.id(), tier) != null) throw new IllegalArgumentException("Duplicate tier ID: " + tier.id());
        if (map.isEmpty()) throw new IllegalArgumentException("No enemy tiers defined");
        tiers = Collections.unmodifiableMap(map);
    }

    public static EnemyTiers defaults() {
        var stream = EnemyTiers.class.getResourceAsStream("/cobbleascend/enemy-tiers.json");
        if (stream == null) throw new IllegalStateException("Missing rules resource: enemy-tiers.json");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read enemy tiers", exception);
        }
    }

    public static EnemyTiers parse(JsonObject root) {
        Json.keys(root, Set.of("schemaVersion", "tiers"), Set.of("status"));
        if (Json.integer(root.get("schemaVersion")) != 1) throw new IllegalArgumentException("Unknown enemy tier schema");
        var list = new ArrayList<EnemyTier>();
        for (var element : Json.array(root.get("tiers"))) {
            var tier = Json.object(element);
            Json.keys(tier, Set.of("id", "rankCredits", "rarities"), Set.of());
            var weights = new ArrayList<EnemyTier.RarityWeight>();
            for (var entry : Json.object(tier.get("rarities")).entrySet())
                weights.add(new EnemyTier.RarityWeight(Rarity.fromId(entry.getKey()), Json.integer(entry.getValue())));
            list.add(new EnemyTier(Json.string(tier.get("id")), weights, Json.integer(tier.get("rankCredits"))));
        }
        return new EnemyTiers(list);
    }

    public EnemyTier tier(String id) {
        var tier = tiers.get(id);
        if (tier == null) throw new IllegalArgumentException("Unknown enemy tier: " + id);
        return tier;
    }

    public Collection<EnemyTier> all() { return tiers.values(); }
}
