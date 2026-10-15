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
    /** Every item stacks to this many. */
    public static final int STACK = 64;

    /** Percent of a withdrawal taken as a fee in the same material, rounded up: a soft sink that scales with the economy. */
    public static final int WITHDRAWAL_FEE_PERCENT = 3;

    /** @param tradable false keeps the material out of vouchers (Tower Marks) */
    public record Spec(MaterialId material, String baseItem, int model, String name, int color, List<String> lore, boolean tradable) {}

    private static final Map<MaterialId, Spec> SPECS = load();

    private CurrencyItems() {}

    public static Spec spec(MaterialId material) {
        return SPECS.get(material);
    }

    /** The fee for withdrawing {@code amount} units, rounded up, so any withdrawal of one or more units costs at least one. */
    public static long withdrawalFee(long amount) {
        return amount < 1 ? 0 : (amount * WITHDRAWAL_FEE_PERCENT + 99) / 100;
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
                    !item.has("tradable") || item.get("tradable").getAsBoolean()));
        }
        return java.util.Collections.unmodifiableMap(out);
    }
}
