package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrencyItemsTest {
    /** Items that are safe to repurpose: they stack to 64, do nothing when used, and have a plain generated model a pack can override. */
    private static final Set<String> INERT_STACKABLE = Set.of("minecraft:glowstone_dust", "minecraft:amethyst_shard", "minecraft:heart_of_the_sea",
            "minecraft:echo_shard", "minecraft:nether_star", "minecraft:prismarine_crystals", "minecraft:nautilus_shell");

    @Test void everyMaterialHasAnItem() {
        for (var material : MaterialId.values()) {
            var spec = CurrencyItems.spec(material);
            assertNotNull(spec, material.id());
            assertFalse(spec.name().isBlank());
            assertFalse(spec.lore().isEmpty());
            assertTrue(spec.color() >= 0 && spec.color() <= 0xFFFFFF);
        }
        assertEquals(MaterialId.values().length, CurrencyItems.all().size());
    }

    @Test void baseItemsAreInertAndDistinctAndModelNumbersNeverCollide() {
        var bases = new HashSet<String>();
        var models = new HashSet<Integer>();
        for (var spec : CurrencyItems.all().values()) {
            assertTrue(INERT_STACKABLE.contains(spec.baseItem()), spec.baseItem() + " is not on the inert, stackable list");
            assertTrue(bases.add(spec.baseItem()), "Two currencies share " + spec.baseItem() + ": a pack override would need two models for one item");
            assertTrue(models.add(spec.model()), "Duplicate model number " + spec.model());
        }
    }

    @Test void aBatchIsACanonicalUuidAndAnythingElseIsNotAVoucher() {
        var id = UUID.randomUUID();
        assertEquals(id, CurrencyItems.parseBatch(id.toString()));
        assertNull(CurrencyItems.parseBatch("not-a-uuid"));
        assertNull(CurrencyItems.parseBatch(""));
        assertNull(CurrencyItems.parseBatch(null));
    }
}
