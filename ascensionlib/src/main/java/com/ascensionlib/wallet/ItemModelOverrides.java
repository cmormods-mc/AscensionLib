package com.ascensionlib.wallet;

import com.cobbleascend.domain.v1.CurrencyItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The server resource pack's half of the item art, with no Polymer in it so it can be tested alone. The items are vanilla items with a
 * {@code custom_model_data} number (1.21.1 has no item model component), so each base item's model file
 * {@code assets/minecraft/models/item/<base>.json} must carry one {@code overrides} entry per number. Two packs that both ship the same
 * base item's file overwrite each other whole, so the entries are MERGED into whatever file is already there instead of replacing it.
 */
public final class ItemModelOverrides {
    public static final String NAMESPACE = "ascensionlib";
    public static final String MODEL_DIRECTORY = "assets/minecraft/models/item/";

    private ItemModelOverrides() {}

    /** The overrides AscensionLib needs, by base item name (without the namespace): every material, its voucher form when tradable, and the Dex. */
    public static Map<String, JsonArray> required() {
        var byBase = new LinkedHashMap<String, JsonArray>();
        for (var spec : CurrencyItems.all().values()) {
            var list = byBase.computeIfAbsent(baseName(spec.baseItem()), k -> new JsonArray());
            list.add(entry(spec.model(), spec.material().id()));
            if (spec.tradable()) list.add(entry(spec.model() + CurrencyItems.VOUCHER_MODEL_OFFSET, spec.material().id() + "_voucher"));
        }
        var dex = CurrencyItems.dex();
        byBase.computeIfAbsent(baseName(dex.baseItem()), k -> new JsonArray()).add(entry(dex.model(), "ascension_dex"));
        return byBase;
    }

    /** {@code assets/minecraft/models/item/glowstone_dust.json} for {@code glowstone_dust}. */
    public static String path(String base) {
        return MODEL_DIRECTORY + base + ".json";
    }

    /** The base item name a model path is for, or null when the path is not an item model file. */
    public static String baseOf(String path) {
        if (!path.startsWith(MODEL_DIRECTORY) || !path.endsWith(".json")) return null;
        var name = path.substring(MODEL_DIRECTORY.length(), path.length() - ".json".length());
        return name.indexOf('/') >= 0 ? null : name;
    }

    /**
     * {@code existing} (the file another pack or vanilla already put there, or null) with every entry of {@code ours} added once. Entries
     * another pack owns are untouched and stay first; an entry of ours that is already present is not repeated, so merging twice changes nothing.
     * A custom_model_data number another pack already maps to a DIFFERENT model is a clash and throws: the numbers are frozen, so the owner of
     * that other pack has to move.
     */
    public static byte[] merge(String base, byte[] existing, JsonArray ours) {
        JsonObject model = existing == null ? defaultModel(base) : JsonParser.parseString(new String(existing, StandardCharsets.UTF_8)).getAsJsonObject();
        var overrides = model.has("overrides") ? model.getAsJsonArray("overrides") : new JsonArray();
        for (var wanted : ours) {
            boolean present = false;
            for (var have : overrides) {
                if (number(have.getAsJsonObject()) != number(wanted.getAsJsonObject())) continue;
                if (!have.getAsJsonObject().get("model").equals(wanted.getAsJsonObject().get("model"))) {
                    throw new IllegalStateException("custom_model_data " + number(wanted.getAsJsonObject()) + " on " + base + " is already mapped to "
                            + have.getAsJsonObject().get("model").getAsString() + " by another pack; AscensionLib needs it for "
                            + wanted.getAsJsonObject().get("model").getAsString());
                }
                present = true;
            }
            if (!present) overrides.add(wanted);
        }
        model.add("overrides", overrides);
        return (model + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject defaultModel(String base) {
        var model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        var textures = new JsonObject();
        textures.addProperty("layer0", "minecraft:item/" + base);
        model.add("textures", textures);
        return model;
    }

    private static JsonObject entry(int number, String model) {
        var predicate = new JsonObject();
        predicate.addProperty("custom_model_data", number);
        var entry = new JsonObject();
        entry.add("predicate", predicate);
        entry.addProperty("model", NAMESPACE + ":item/" + model);
        return entry;
    }

    private static int number(JsonObject entry) {
        var predicate = entry.has("predicate") ? entry.getAsJsonObject("predicate") : null;
        return predicate != null && predicate.has("custom_model_data") ? predicate.get("custom_model_data").getAsInt() : -1;
    }

    private static String baseName(String itemId) {
        int colon = itemId.indexOf(':');
        return colon < 0 ? itemId : itemId.substring(colon + 1);
    }
}
