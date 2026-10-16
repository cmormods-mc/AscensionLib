package com.ascensionlib.wallet;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.CurrencyItems;
import com.cobbleascend.domain.v1.MaterialId;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WalletItemsTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void everyTooltipOpensWithTheRarityInItsColourThenTheKind() {
        for (var material : MaterialId.values()) {
            var spec = CurrencyItems.spec(material);
            var rarity = CurrencyItems.rarity(spec.rarity());
            for (var stack : new net.minecraft.world.item.ItemStack[] {WalletItems.create(material, 1, null), WalletItems.create(material, 1, UUID.randomUUID())}) {
                var first = stack.get(DataComponents.LORE).lines().get(0);
                assertEquals(rarity.label() + " · " + spec.kind(), first.getString(), material.id());
                assertEquals(rarity.color(), first.getStyle().getColor().getValue(), material.id() + " rarity colour");
                assertTrue(first.getStyle().isBold(), "the rarity word is bold");
                assertEquals(spec.lore().get(0), stack.get(DataComponents.LORE).lines().get(1).getString(), "then the item's own lore");
            }
        }
    }

    @Test void everyMaterialRoundTripsAsLootFormAndAsAVoucher() {
        var batch = UUID.randomUUID();
        for (var material : MaterialId.values()) {
            var loot = WalletItems.create(material, 5, null);
            assertEquals(5, loot.getCount());
            var parsedLoot = WalletItems.parse(loot).orElseThrow();
            assertEquals(material, parsedLoot.material());
            assertNull(parsedLoot.batch(), "Loot form has no batch");
            var voucher = WalletItems.create(material, 64, batch);
            var parsed = WalletItems.parse(voucher).orElseThrow();
            assertEquals(material, parsed.material());
            assertEquals(batch, parsed.batch());
            assertEquals(CurrencyItems.spec(material).model() + CurrencyItems.VOUCHER_MODEL_OFFSET, voucher.get(DataComponents.CUSTOM_MODEL_DATA).value(), "a voucher has its own picture");
            assertEquals(CurrencyItems.spec(material).model(), loot.get(DataComponents.CUSTOM_MODEL_DATA).value(), "loot form keeps the plain one");
            assertEquals(CurrencyItems.spec(material).name(), voucher.get(DataComponents.CUSTOM_NAME).getString());
        }
    }

    @Test void aStackIsCappedAtSixtyFourAndTwoBatchesNeverStackTogether() {
        assertEquals(64, WalletItems.create(MaterialId.FACET, 500, null).getCount());
        var a = WalletItems.create(MaterialId.FACET, 10, UUID.randomUUID());
        var b = WalletItems.create(MaterialId.FACET, 10, UUID.randomUUID());
        assertFalse(ItemStack.isSameItemSameComponents(a, b), "Different batches must stay separate stacks");
        assertTrue(ItemStack.isSameItemSameComponents(a, WalletItems.create(MaterialId.FACET, 3, UUID.fromString(WalletItems.parse(a).orElseThrow().batch().toString()))));
        assertTrue(ItemStack.isSameItemSameComponents(WalletItems.create(MaterialId.FACET, 1, null), WalletItems.create(MaterialId.FACET, 9, null)),
                "Loot form stacks with loot form");
    }

    @Test void aTagOnTheWrongBaseItemOrWithAMalformedBatchOrAnUnknownMaterialIsNotCurrency() {
        var dirt = new ItemStack(Items.DIRT, 3);
        var inner = new CompoundTag();
        inner.putString("material", "resonance_dust");
        var tag = new CompoundTag();
        tag.put(CurrencyItems.TAG, inner);
        dirt.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertTrue(WalletItems.parse(dirt).isEmpty(), "A tagged dirt block is not dust");

        var bad = WalletItems.create(MaterialId.FACET, 1, null);
        var badInner = new CompoundTag();
        badInner.putString("material", "facet");
        badInner.putString("batch", "forged");
        var badTag = new CompoundTag();
        badTag.put(CurrencyItems.TAG, badInner);
        bad.set(DataComponents.CUSTOM_DATA, CustomData.of(badTag));
        assertTrue(WalletItems.parse(bad).isEmpty(), "A malformed batch id is not a voucher");

        var unknown = WalletItems.create(MaterialId.FACET, 1, null);
        var unknownInner = new CompoundTag();
        unknownInner.putString("material", "gold_bar");
        var unknownTag = new CompoundTag();
        unknownTag.put(CurrencyItems.TAG, unknownInner);
        unknown.set(DataComponents.CUSTOM_DATA, CustomData.of(unknownTag));
        assertTrue(WalletItems.parse(unknown).isEmpty());
        assertTrue(WalletItems.parse(ItemStack.EMPTY).isEmpty());
        assertTrue(WalletItems.parse(new ItemStack(Items.AMETHYST_SHARD)).isEmpty(), "A plain amethyst shard is not a Facet");
    }
}
