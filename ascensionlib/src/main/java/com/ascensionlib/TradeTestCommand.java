package com.ascensionlib;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.trade.ActiveTrade;
import com.cobblemon.mod.common.trade.PlayerTradeParticipant;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test seam: /ascendtesttrade <a> <b> <slotA> <slotB> drives one real Cobblemon trade between two online players
 * without the trade screen, so a headless test can check that a profile follows the Pokemon. It exists only when the JVM
 * is started with -Dascensionlib.testTrade=true; otherwise {@link #node()} is null and nothing is registered.
 */
final class TradeTestCommand {
    private static final Logger LOG = LoggerFactory.getLogger("AscensionLib/TradeTest");
    static final boolean ENABLED = Boolean.getBoolean("ascensionlib.testTrade");

    private TradeTestCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        if (!ENABLED) return null;
        return Commands.literal("ascendtesttrade").requires(source -> source.hasPermission(2))
                .then(Commands.argument("a", EntityArgument.player())
                        .then(Commands.argument("b", EntityArgument.player())
                                .then(Commands.argument("slotA", IntegerArgumentType.integer(1, 6))
                                        .then(Commands.argument("slotB", IntegerArgumentType.integer(1, 6))
                                                .executes(ctx -> run(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "a"), EntityArgument.getPlayer(ctx, "b"),
                                                        IntegerArgumentType.getInteger(ctx, "slotA"),
                                                        IntegerArgumentType.getInteger(ctx, "slotB")))))));
    }

    /** /ascendtestcraft <player> <partySlot> <kind> <slotId|-> : run one craft confirm as that player, on the Pokemon in that party slot. */
    static LiteralArgumentBuilder<CommandSourceStack> craftNode() {
        if (!ENABLED) return null;
        return Commands.literal("ascendtestcraft").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("slotId", com.mojang.brigadier.arguments.StringArgumentType.string())
                                                .executes(ctx -> craft(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "slot"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "slotId")))))));
    }

    /** /ascendtestcraftas <player> <pokemonUuid> <kind> <slotId>: same, but for a Pokemon the player may not own (must be refused). */
    static LiteralArgumentBuilder<CommandSourceStack> craftAsNode() {
        if (!ENABLED) return null;
        return Commands.literal("ascendtestcraftas").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("pokemon", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("slotId", com.mojang.brigadier.arguments.StringArgumentType.string())
                                                .executes(ctx -> {
                                                    com.ascensionlib.craft.CraftNet.testConfirm(EntityArgument.getPlayer(ctx, "player"),
                                                            com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "pokemon"),
                                                            com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind"),
                                                            com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "slotId"));
                                                    return 1;
                                                })))));
    }

    /** /ascendtestlock <player> <partySlot>: mark that Pokemon as a loan, the way CobbleTowers does for rentals. */
    static LiteralArgumentBuilder<CommandSourceStack> lockNode() {
        if (!ENABLED) return null;
        return Commands.literal("ascendtestlock").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> {
                                    var player = EntityArgument.getPlayer(ctx, "player");
                                    var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(IntegerArgumentType.getInteger(ctx, "slot") - 1);
                                    if (pokemon == null) { ctx.getSource().sendFailure(Component.literal("That party slot is empty.")); return 0; }
                                    pokemon.getPersistentData().putBoolean(CraftLocks.TAG, true);
                                    LOG.info("LOCK-TEST locked {} for {}", pokemon.getUuid(), player.getGameProfile().getName());
                                    return 1;
                                })));
    }

    /**
     * /ascendtestescrow <seller> <slot> <buyer>: the exact sequence SnobbleAuctions uses (save the Pokemon to compressed NBT, remove it from
     * the party, later decode it and add it to the buyer's party), with no money or page. Seller and buyer may be the same player (a cancel).
     */
    static LiteralArgumentBuilder<CommandSourceStack> escrowNode() {
        if (!ENABLED) return null;
        return Commands.literal("ascendtestescrow").requires(source -> source.hasPermission(2))
                .then(Commands.argument("seller", EntityArgument.player())
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .then(Commands.argument("buyer", EntityArgument.player())
                                        .executes(ctx -> escrow(ctx.getSource(), EntityArgument.getPlayer(ctx, "seller"),
                                                IntegerArgumentType.getInteger(ctx, "slot"), EntityArgument.getPlayer(ctx, "buyer"))))));
    }

    private static int escrow(CommandSourceStack source, ServerPlayer seller, int slot, ServerPlayer buyer) {
        try {
            var registries = source.getServer().registryAccess();
            var storage = Cobblemon.INSTANCE.getStorage();
            var party = storage.getParty(seller);
            var mon = party.get(slot - 1);
            if (mon == null) { source.sendFailure(Component.literal("That party slot is empty.")); return 0; }
            var out = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(mon.saveToNBT(registries, new net.minecraft.nbt.CompoundTag()), out);
            String blob = java.util.Base64.getEncoder().encodeToString(out.toByteArray());
            boolean removed = party.remove(mon);
            LOG.info("ESCROW-TEST listed {} from {} (removed={}, {} bytes of NBT)", mon.getUuid(), seller.getGameProfile().getName(), removed, blob.length());
            var tag = net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(blob)),
                    net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var copy = new com.cobblemon.mod.common.pokemon.Pokemon().loadFromNBT(registries, tag);
            var target = storage.getParty(buyer);
            boolean added = target.add(copy);
            LOG.info("ESCROW-TEST delivered {} to {} (added={}, same uuid={}, owner={})", copy.getUuid(), buyer.getGameProfile().getName(), added,
                    copy.getUuid().equals(mon.getUuid()), copy.getOwnerUUID());
            return added ? 1 : 0;
        } catch (java.io.IOException | RuntimeException exception) {
            LOG.error("ESCROW-TEST failed", exception);
            return 0;
        }
    }

    private static int craft(CommandSourceStack source, ServerPlayer player, int slot, String kind, String slotId) {
        var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) { source.sendFailure(Component.literal("That party slot is empty.")); return 0; }
        LOG.info("CRAFT-TEST {} {} on {} slot {} by {}", kind, slotId, pokemon.getUuid(), slot, player.getGameProfile().getName());
        com.ascensionlib.craft.CraftNet.testConfirm(player, pokemon.getUuid().toString(), kind, slotId);
        return 1;
    }

    private static int run(CommandSourceStack source, ServerPlayer a, ServerPlayer b, int slotA, int slotB) {
        var storage = Cobblemon.INSTANCE.getStorage();
        var pokemonA = storage.getParty(a).get(slotA - 1);
        var pokemonB = storage.getParty(b).get(slotB - 1);
        if (pokemonA == null || pokemonB == null) {
            source.sendFailure(Component.literal("Both party slots must hold a Pokemon."));
            return 0;
        }
        LOG.info("TRADE-TEST start: {} gives {} ({}) and {} gives {} ({})", a.getGameProfile().getName(), pokemonA.getUuid(), pokemonA.getSpecies().getName(),
                b.getGameProfile().getName(), pokemonB.getUuid(), pokemonB.getSpecies().getName());
        var participantA = new PlayerTradeParticipant(a);
        var participantB = new PlayerTradeParticipant(b);
        var trade = new ActiveTrade(participantA, participantB);
        trade.updateOffer(participantA, pokemonA);
        trade.updateOffer(participantB, pokemonB);
        trade.updateAcceptance(participantA, true);
        trade.updateAcceptance(participantB, true);
        // On a real client the swap runs when the trade animation ends; there is no client here, so end it ourselves.
        trade.performTrade(participantA);
        source.sendSuccess(() -> Component.literal("Trade started; watch the log for TRADE-TEST and run inspect afterwards."), false);
        return 1;
    }
}
