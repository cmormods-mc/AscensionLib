package com.ascensionlib.dex;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensionlib.wallet.WalletItems;
import com.cobbleascend.domain.v1.CurrencyItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DexItemTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void theDexIsTheNamedBookWithOurTagAndModelAndIsRecognised() {
        var dex = DexItem.create();
        assertTrue(DexItem.isDex(dex));
        assertEquals(Items.KNOWLEDGE_BOOK, dex.getItem());
        assertEquals("AscensionDex", dex.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(CurrencyItems.dex().model(), dex.get(DataComponents.CUSTOM_MODEL_DATA).value());
        assertEquals(1, dex.get(DataComponents.MAX_STACK_SIZE));
        assertFalse(dex.get(DataComponents.LORE).lines().isEmpty());
    }

    @Test void onlyOurTagOnTheRightBaseItemCounts() {
        assertFalse(DexItem.isDex(ItemStack.EMPTY));
        assertFalse(DexItem.isDex(new ItemStack(Items.KNOWLEDGE_BOOK)), "a plain book is not a Dex");
        var inner = new CompoundTag();
        inner.putBoolean("dex", true);
        var tag = new CompoundTag();
        tag.put(CurrencyItems.TAG, inner);
        var dirt = new ItemStack(Items.DIRT);
        dirt.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertFalse(DexItem.isDex(dirt), "a tagged dirt block is not a Dex");
    }

    @Test void theWalletNeverMistakesTheDexForCurrency() {
        assertTrue(WalletItems.parse(DexItem.create()).isEmpty());
    }

    @Test void theDexBaseItemIsInertAndDistinctFromEveryCurrency() {
        var dex = CurrencyItems.dex();
        for (var spec : CurrencyItems.all().values()) {
            assertNotEquals(dex.baseItem(), spec.baseItem());
            assertNotEquals(dex.model(), spec.model());
        }
    }
}
