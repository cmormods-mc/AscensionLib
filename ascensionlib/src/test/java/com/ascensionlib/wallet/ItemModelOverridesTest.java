package com.ascensionlib.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbleascend.domain.v1.CurrencyItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class ItemModelOverridesTest {
    private static JsonObject parse(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject entry(int number, String model) {
        var predicate = new JsonObject();
        predicate.addProperty("custom_model_data", number);
        var entry = new JsonObject();
        entry.add("predicate", predicate);
        entry.addProperty("model", model);
        return entry;
    }

    private static JsonArray one(JsonObject entry) {
        var list = new JsonArray();
        list.add(entry);
        return list;
    }

    @Test void everyMaterialVoucherAndTheDexAreCovered() {
        var required = ItemModelOverrides.required();
        var numbers = new HashSet<Integer>();
        required.values().forEach(list -> list.forEach(e -> assertTrue(numbers.add(e.getAsJsonObject().getAsJsonObject("predicate").get("custom_model_data").getAsInt()),
                "a custom model number is used twice")));
        int expected = 1 + CurrencyItems.all().values().stream().mapToInt(spec -> spec.tradable() ? 2 : 1).sum();
        assertEquals(expected, numbers.size());
        assertTrue(numbers.contains(CurrencyItems.dex().model()));
        assertTrue(required.containsKey("glowstone_dust"), "Resonance Dust draws on glowstone dust");
    }

    @Test void aMissingFileGetsTheDefaultModelWithOurEntries() {
        var merged = parse(ItemModelOverrides.merge("nether_star", null, ItemModelOverrides.required().get("nether_star")));
        assertEquals("minecraft:item/generated", merged.get("parent").getAsString());
        assertEquals("minecraft:item/nether_star", merged.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals(ItemModelOverrides.required().get("nether_star").size(), merged.getAsJsonArray("overrides").size());
    }

    @Test void anotherPacksEntriesStayFirstAndOursAreAdded() {
        var theirs = parse("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/glowstone_dust\"},\"overrides\":[{\"predicate\":{\"custom_model_data\":2},\"model\":\"moresparkles:item/shiny_charm\"}]}"
                .getBytes(StandardCharsets.UTF_8)).toString().getBytes(StandardCharsets.UTF_8);
        var ours = ItemModelOverrides.required().get("glowstone_dust");
        var overrides = parse(ItemModelOverrides.merge("glowstone_dust", theirs, ours)).getAsJsonArray("overrides");
        assertEquals(1 + ours.size(), overrides.size());
        assertEquals("moresparkles:item/shiny_charm", overrides.get(0).getAsJsonObject().get("model").getAsString());
    }

    /** Regression: SnobbleCrates' 7286001.. on nautilus_shell and SnobbleHolograms' 740005.. on knowledge_book were shadowed by our 8107/8110. */
    @Test void anotherPacksHigherNumbersComeAfterOursSoTheyWin() {
        var theirs = ("{\"overrides\":[{\"predicate\":{\"custom_model_data\":7286001},\"model\":\"snobblecrates:crate/root\"}]}").getBytes(StandardCharsets.UTF_8);
        var overrides = parse(ItemModelOverrides.merge("nautilus_shell", theirs, ItemModelOverrides.required().get("nautilus_shell"))).getAsJsonArray("overrides");
        var last = overrides.get(overrides.size() - 1).getAsJsonObject();
        assertEquals("snobblecrates:crate/root", last.get("model").getAsString());
        int previous = Integer.MIN_VALUE;
        for (var e : overrides) {
            int n = e.getAsJsonObject().getAsJsonObject("predicate").get("custom_model_data").getAsInt();
            assertTrue(n >= previous, "overrides must ascend by number");
            previous = n;
        }
    }

    @Test void mergingTwiceChangesNothing() {
        var ours = ItemModelOverrides.required().get("knowledge_book");
        var once = ItemModelOverrides.merge("knowledge_book", null, ours);
        assertEquals(new String(once, StandardCharsets.UTF_8), new String(ItemModelOverrides.merge("knowledge_book", once, ours), StandardCharsets.UTF_8));
    }

    @Test void aNumberAnotherPackAlreadyMapsElsewhereIsAClash() {
        var number = ItemModelOverrides.required().get("glowstone_dust").get(0).getAsJsonObject().getAsJsonObject("predicate").get("custom_model_data").getAsInt();
        var theirs = "{\"overrides\":[{\"predicate\":{\"custom_model_data\":%d},\"model\":\"other:item/thing\"}]}".formatted(number).getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, () -> ItemModelOverrides.merge("glowstone_dust", theirs, ItemModelOverrides.required().get("glowstone_dust")));
    }

    @Test void onlyItemModelFilesAreRecognised() {
        assertEquals("glowstone_dust", ItemModelOverrides.baseOf("assets/minecraft/models/item/glowstone_dust.json"));
        assertNull(ItemModelOverrides.baseOf("assets/minecraft/models/item/sub/dir.json"));
        assertNull(ItemModelOverrides.baseOf("assets/minecraft/textures/item/glowstone_dust.png"));
        assertNull(ItemModelOverrides.baseOf("assets/ascensionlib/models/item/facet.json"));
    }

    /** The checked-in pack (tools/build_resourcepack.py) and the runtime merge must agree on every base item's entries. */
    @Test void theRuntimeEntriesMatchTheBuiltPackFiles() throws Exception {
        for (var entry : ItemModelOverrides.required().entrySet()) {
            Path file = Path.of("..", "resourcepack", ItemModelOverrides.path(entry.getKey()));
            if (!Files.exists(file)) continue; // the pack folder is not part of every checkout
            var packed = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("overrides");
            assertEquals(packed, entry.getValue(), "overrides for " + entry.getKey() + " differ from resourcepack/; re-run tools/build_resourcepack.py");
            assertFalse(entry.getValue().isEmpty());
        }
    }
}
