package com.ascensionlib.dex;

import com.ascensionlib.net.RateLimit;
import com.cobbleascend.domain.v1.CurrencyItems;
import java.util.ArrayList;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The AscensionDex: an item a player right-clicks to review their party and pick a Pokemon to inspect. It is a vanilla item (a knowledge book, which does
 * nothing when used) carrying a tag in its custom data and a custom model number; the server resource pack reskins it (design/currency-items.json,
 * {@code "dex"}). The tag is the whole identity, so there is nothing to register and a player without the pack still holds a working, plainly named book.
 * Using it only opens {@link PartyWeb}; it spends and changes nothing, so it can be given out freely.
 */
public final class DexItem {
    /** The key inside the library's custom data tag. */
    static final String KEY = "dex";

    private DexItem() {}

    /** Hooks the right click. Called once at startup. */
    public static void install() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getItemInHand(hand);
            if (!isDex(stack)) return InteractionResultHolder.pass(stack);
            // Handled on both sides so the client plays no vanilla use of the book; only the server opens the page.
            if (player instanceof ServerPlayer server && RateLimit.allow(server.getUUID(), "dex.use", 3, 1)) PartyWeb.open(server);
            return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
        });
    }

    public static ItemStack create() {
        var spec = CurrencyItems.dex();
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(spec.baseItem()));
        var stack = new ItemStack(item, 1);
        var inner = new CompoundTag();
        inner.putBoolean(KEY, true);
        var tag = new CompoundTag();
        tag.put(CurrencyItems.TAG, inner);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(spec.model()));
        var plain = Style.EMPTY.withItalic(false);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(spec.name()).withStyle(plain.withColor(TextColor.fromRgb(spec.color()))));
        var lore = new ArrayList<Component>();
        lore.add(com.ascensionlib.wallet.WalletItems.rarityLine(spec.rarity(), spec.kind()));
        for (var line : spec.lore()) lore.add(Component.literal(line).withStyle(plain.withColor(net.minecraft.ChatFormatting.GRAY)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        return stack;
    }

    /** Our tag on the base item the Dex uses (a tagged dirt block is not a Dex). */
    public static boolean isDex(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        var root = data.copyTag();
        if (!root.contains(CurrencyItems.TAG, Tag.TAG_COMPOUND) || !root.getCompound(CurrencyItems.TAG).getBoolean(KEY)) return false;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(CurrencyItems.dex().baseItem());
    }

    /** Gives the player one, dropping it at their feet when the inventory is full. */
    public static void give(ServerPlayer player) {
        var stack = create();
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    /** Whether the player already carries one (the command only hands out another when they do not). */
    public static boolean carries(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (isDex(inventory.getItem(slot))) return true;
        return false;
    }
}
