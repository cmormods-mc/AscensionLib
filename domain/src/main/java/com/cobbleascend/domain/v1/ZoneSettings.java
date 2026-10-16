package com.cobbleascend.domain.v1;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where profiles act and how rare a rated Pokemon is, as the owner tunes it in {@code config/ascensionlib.json} (docs/ALL-WILD-PROFILES-DESIGN.md).
 *
 * <ul>
 *   <li>{@code defaultTier}: the tier of every dimension nothing else names. 1 is the ordinary rarity table. 0 turns profiles off there: wild and trainer
 *       fights are native and captures get no profile.</li>
 *   <li>{@code dimensions}: a tier (0 to {@link #MAX_TIER}) for a named dimension, which beats everything else, including a zone another mod registered.</li>
 *   <li>{@code rarityTilt}: how fast each tier above 1 shifts weight to rarer results. See {@link ZoneRarity}.</li>
 *   <li>{@code rarityWeights}: an explicit weight list (six numbers, Common to Mythic) for a tier, replacing the tilt for that tier alone.</li>
 * </ul>
 */
public record ZoneSettings(int defaultTier, Map<String, Integer> dimensions, double rarityTilt, Map<Integer, List<Integer>> rarityWeights) {
    public static final int MAX_TIER = 10;
    public static final double DEFAULT_TILT = 0.30;
    public static final ZoneSettings DEFAULTS = new ZoneSettings(1, Map.of(), DEFAULT_TILT, Map.of());

    public ZoneSettings {
        if (defaultTier < 0 || defaultTier > MAX_TIER) throw new IllegalArgumentException("defaultTier is 0-" + MAX_TIER);
        if (!(rarityTilt >= 0 && rarityTilt <= 2)) throw new IllegalArgumentException("rarityTilt is 0 to 2");
        dimensions = Map.copyOf(dimensions);
        for (var entry : dimensions.entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("\"" + entry.getKey() + "\" is not a dimension id such as minecraft:the_nether");
            if (entry.getValue() < 0 || entry.getValue() > MAX_TIER) throw new IllegalArgumentException(entry.getKey() + " tier is 0-" + MAX_TIER);
        }
        var copy = new TreeMap<Integer, List<Integer>>();
        for (var entry : rarityWeights.entrySet()) {
            int tier = entry.getKey();
            var list = entry.getValue();
            if (tier < 1 || tier > MAX_TIER) throw new IllegalArgumentException("rarityWeights tier is 1-" + MAX_TIER);
            if (list.size() != com.cobbleascend.domain.Rarity.values().length) throw new IllegalArgumentException("rarityWeights for tier " + tier + " needs " + com.cobbleascend.domain.Rarity.values().length + " numbers");
            long sum = 0;
            for (int w : list) {
                if (w < 0 || w > 100_000_000) throw new IllegalArgumentException("rarityWeights for tier " + tier + " must be 0 to 100000000");
                sum += w;
            }
            if (sum <= 0 || sum > Integer.MAX_VALUE) throw new IllegalArgumentException("rarityWeights for tier " + tier + " must add up to more than 0");
            copy.put(tier, List.copyOf(list));
        }
        rarityWeights = Map.copyOf(copy);
    }

    public JsonObject toJson() {
        var o = new JsonObject();
        o.addProperty("defaultTier", defaultTier);
        var dims = new JsonObject();
        new TreeMap<>(dimensions).forEach(dims::addProperty);
        o.add("dimensions", dims);
        o.addProperty("rarityTilt", rarityTilt);
        var weights = new JsonObject();
        new TreeMap<>(rarityWeights).forEach((tier, list) -> {
            var a = new JsonArray();
            list.forEach(a::add);
            weights.add(String.valueOf(tier), a);
        });
        o.add("rarityWeights", weights);
        return o;
    }

    /** Reads the section; throws {@link IllegalArgumentException} with a message the owner can act on when a value does not fit (the caller keeps the defaults). */
    public static ZoneSettings fromJson(JsonObject o, ZoneSettings fallback) {
        int tier = Tunables.intOf(o, "defaultTier", fallback.defaultTier());
        double tilt = fallback.rarityTilt();
        var t = o.get("rarityTilt");
        if (t != null && !t.isJsonNull()) {
            if (!t.isJsonPrimitive() || !t.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("rarityTilt must be a number");
            tilt = t.getAsDouble();
        }
        var dims = new LinkedHashMap<String, Integer>(fallback.dimensions());
        var d = Tunables.obj(o, "dimensions");
        if (d != null) {
            dims.clear();
            for (var key : d.keySet()) dims.put(key, Tunables.intOf(d, key, 0));
        }
        var weights = new LinkedHashMap<Integer, List<Integer>>(fallback.rarityWeights());
        var w = Tunables.obj(o, "rarityWeights");
        if (w != null) {
            weights.clear();
            for (var key : w.keySet()) {
                int tierKey;
                try {
                    tierKey = Integer.parseInt(key);
                } catch (NumberFormatException bad) {
                    throw new IllegalArgumentException("rarityWeights keys are tier numbers, not \"" + key + "\"");
                }
                var arr = w.get(key);
                if (!arr.isJsonArray()) throw new IllegalArgumentException("rarityWeights." + key + " must be a list of numbers");
                var list = new java.util.ArrayList<Integer>();
                for (var e : arr.getAsJsonArray()) {
                    if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || e.getAsDouble() != Math.rint(e.getAsDouble()))
                        throw new IllegalArgumentException("rarityWeights." + key + " must be whole numbers");
                    list.add(e.getAsInt());
                }
                weights.put(tierKey, list);
            }
        }
        return new ZoneSettings(tier, dims, tilt, weights);
    }
}
