package com.cobbleascend.domain.v1;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Each wallet material as a vanilla item (design/currency-items.json): a base item, a custom model number a server resource pack maps to art, a
 * display name, a colour and lore. Pure data, no Minecraft types; the mod builds and reads the real item stacks from it.
 *
 * <p>An item with no batch is <b>loot form</b>: loot tables hand it out and the server absorbs it into the wallet when it is carried. An item
 * with a batch id is a <b>voucher</b>: minted by a wallet withdrawal, tradable, and redeemed only up to what its batch still has outstanding
 * (so a duplicated stack cannot inflate the currency).
 */
public final class CurrencyItems {
    /** The key of the tag in the item's custom data, under which {@code material} and the optional {@code batch} live. */
    public static final String TAG = "ascensionlib";
    public static final String MATERIAL_KEY = "material";
    public static final String BATCH_KEY = "batch";
    /** A voucher's custom model number is its loot-form number plus this, so the pack can draw the two differently (a paper tag and a seal on a voucher). */
    public static final int VOUCHER_MODEL_OFFSET = 1000;
    /** Every item stacks to this many. */
    public static final int STACK = 64;


    /** @param tradable false keeps the material out of vouchers (Tower Marks) */
    public record Spec(MaterialId material, String baseItem, int model, String name, int color, List<String> lore, boolean tradable, String rarity, String kind) {}

    /** The AscensionDex item: a base item, a custom model number the pack maps to art, a name, a colour and lore (not a currency, so not in {@link #all()}). */
    public record DexSpec(String baseItem, int model, String name, int color, List<String> lore, String rarity, String kind) {}

    /** A rarity tier: the word on the tooltip and its colour (the same colours as the rarity tags on the pages). */
    public record Rarity(String id, String label, int color) {}

    private static final Map<MaterialId, Spec> SPECS = load();
    private static final DexSpec DEX = loadDex();
    private static final Map<String, Rarity> RARITIES = loadRarities();

    private CurrencyItems() {}

    public static Spec spec(MaterialId material) {
        return SPECS.get(material);
    }

    /** The fee for withdrawing {@code amount} units: a percent ({@link Tunables#withdrawalFeePercent}, 3 by default) taken in the same material, rounded up, so any withdrawal of one or more units costs at least one when the fee is above zero. */
    public static long withdrawalFee(long amount) {
        return amount < 1 ? 0 : (amount * Tunables.current().withdrawalFeePercent() + 99) / 100;
    }

    /** The rarity tier with this id (every item names one that exists; an unknown id falls back to common). */
    public static Rarity rarity(String id) {
        var found = RARITIES.get(id);
        return found != null ? found : RARITIES.get("common");
    }

    public static DexSpec dex() {
        return DEX;
    }

    public static Map<MaterialId, Spec> all() {
        return SPECS;
    }

    /** A batch id is a canonical UUID; anything else is not a voucher. */
    public static UUID parseBatch(String text) {
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, Rarity> loadRarities() {
        var stream = CurrencyItems.class.getResourceAsStream("/cobbleascend/currency-items.json");
        if (stream == null) throw new IllegalStateException("currency-items.json is missing from the jar");
        var rarities = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("rarities");
        if (rarities == null || !rarities.has("common")) throw new IllegalStateException("currency-items.json needs a rarities table with common");
        var out = new java.util.LinkedHashMap<String, Rarity>();
        for (var entry : rarities.entrySet()) {
            var r = entry.getValue().getAsJsonObject();
            out.put(entry.getKey(), new Rarity(entry.getKey(), r.get("label").getAsString(), Integer.parseInt(r.get("color").getAsString().substring(1), 16)));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static DexSpec loadDex() {
        var stream = CurrencyItems.class.getResourceAsStream("/cobbleascend/currency-items.json");
        if (stream == null) throw new IllegalStateException("currency-items.json is missing from the jar");
        var dex = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("dex");
        if (dex == null) throw new IllegalStateException("No dex item in currency-items.json");
        var lore = new java.util.ArrayList<String>();
        dex.getAsJsonArray("lore").forEach(line -> lore.add(line.getAsString()));
        return new DexSpec(dex.get("base").getAsString(), dex.get("model").getAsInt(), dex.get("name").getAsString(),
                Integer.parseInt(dex.get("color").getAsString().substring(1), 16), List.copyOf(lore),
                dex.get("rarity").getAsString(), dex.get("kind").getAsString());
    }

    private static Map<MaterialId, Spec> load() {
        var stream = CurrencyItems.class.getResourceAsStream("/cobbleascend/currency-items.json");
        if (stream == null) throw new IllegalStateException("currency-items.json is missing from the jar");
        var items = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("items");
        var out = new EnumMap<MaterialId, Spec>(MaterialId.class);
        for (var material : MaterialId.values()) {
            var item = items.getAsJsonObject(material.id());
            if (item == null) throw new IllegalStateException("No currency item for " + material.id());
            var lore = new java.util.ArrayList<String>();
            item.getAsJsonArray("lore").forEach(line -> lore.add(line.getAsString()));
            out.put(material, new Spec(material, item.get("base").getAsString(), item.get("model").getAsInt(), item.get("name").getAsString(),
                    Integer.parseInt(item.get("color").getAsString().substring(1), 16), List.copyOf(lore),
                    !item.has("tradable") || item.get("tradable").getAsBoolean(), item.get("rarity").getAsString(), item.get("kind").getAsString()));
        }
        return java.util.Collections.unmodifiableMap(out);
    }
}
