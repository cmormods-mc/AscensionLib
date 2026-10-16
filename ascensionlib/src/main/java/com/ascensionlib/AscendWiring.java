package com.ascensionlib;

import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.Origin;
import com.cobbleascend.domain.v1.ProfileV1;
import com.cobbleascend.domain.v1.RankedRules;
import com.ascensionlib.battle.ProfileZones;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.LevelUpEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cobblemon capture, hatch and level wiring and the {@code /ascend} commands over the canonical store. Combat
 * effects, crafting screens and Trials are NOT active; the commands here read state or are operator-only.
 */
final class AscendWiring {
    private static final Logger LOG = LoggerFactory.getLogger(AscensionLib.MOD_ID);
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private final Map<UUID, PendingAcquisition> pending = new LinkedHashMap<>();
    /** Pokemon waiting to be reconciled after a login: a PC of hundreds would otherwise cost one long tick (a store read each). */
    private final java.util.ArrayDeque<SweepJob> sweepQueue = new java.util.ArrayDeque<>();
    private static final int SWEEP_PER_TICK = 30;
    private static final int SWEEP_QUEUE_LIMIT = 20_000;
    private MinecraftServer server;

    private record SweepJob(UUID player, Pokemon pokemon) {}

    private record PendingAcquisition(Pokemon pokemon, UUID owner, Origin origin, boolean rollRarity, int tier,
                                      MinecraftServer server, int queuedTick) {}

    void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STARTED.register(com.ascensionlib.BackupJob::started);
        ServerTickEvents.END_SERVER_TICK.register(com.ascensionlib.BackupJob::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stop);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> joined.execute(() -> {
            enqueueSweep(handler.getPlayer());
            safely("voucher delivery", () -> com.ascensionlib.wallet.WalletItems.deliverPending(handler.getPlayer()));
        }));
        ServerTickEvents.END_SERVER_TICK.register(com.ascensionlib.wallet.WalletItems::tick);
        com.ascensionlib.dex.DexItem.install();
        ServerTickEvents.END_SERVER_TICK.register(com.ascensionlib.battle.BleedWatch::tick);
        ServerTickEvents.END_SERVER_TICK.register(com.ascensionlib.battle.Participation::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, left) -> sweepQueue.removeIf(job -> job.player().equals(handler.getPlayer().getUUID())));
        CobblemonEvents.POKEMON_CAPTURED.subscribe((Consumer<PokemonCapturedEvent>) event -> {
            // Every wild capture rolls a profile at its dimension's tier (1, the ordinary table, unless the configuration or a mod says otherwise); the
            // ball is thrown from the dimension the wild Pokemon is in. A dimension set to tier 0 gives none: the Pokemon stays unprofiled until a Sigil.
            int tier = ProfileZones.tierOf(event.getPlayer().level().dimension().location());
            if (tier < 1) return;
            var s = event.getPlayer().getServer();
            if (s != null) s.execute(() -> queue(event.getPokemon(), event.getPlayer().getUUID(), Origin.of("wild_capture"), true, tier, s));
        });
        // A hatched egg is profiled too, at the ordinary table whatever dimension it hatches in (the Pokemon, not the place, earns the profile).
        CobblemonEvents.HATCH_EGG_POST.subscribe((Consumer<com.cobblemon.mod.common.api.events.pokemon.HatchEggEvent.Post>) event -> {
            var s = event.getPlayer().getServer();
            if (s != null) s.execute(() -> queue(event.getPokemon(), event.getPlayer().getUUID(), Origin.of("hatch"), true, 1, s));
        });
        CobblemonEvents.LEVEL_UP_EVENT.subscribe((Consumer<LevelUpEvent>) event -> {
            // The event can fire before the new level is applied; read the authoritative level on the next task.
            var s = server;
            if (s != null) s.execute(() -> {
                var service = service();
                if (service != null) safely("level catch-up", () -> service.observeLevel(event.getPokemon()));
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(this::finishAcquisitions);
        ServerTickEvents.END_SERVER_TICK.register(this::sweepSome);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(buildCommand()));
        LOG.info("Capture and hatch rarity, wild and trainer ratings, level milestones and /ascend inspection enabled. Battle effects act only when CobbleRaids is installed (they are untested in a live battle); crafting and Trials are NOT active.");
    }

    private void stop(MinecraftServer stopping) {
        sweepQueue.clear();
        pending.entrySet().removeIf(e -> e.getValue().server() == stopping);
        server = null;
    }

    /** The library's service for the running world, or null while there is none or progression is disabled. */
    private static ProfileService service() { return AscensionApi.service().orElse(null); }

    private void safely(String what, Runnable action) {
        try { action.run(); } catch (RuntimeException exception) { LOG.error("{} failed; data preserved", what, exception); }
    }

    // --- login sweep -----------------------------------------------------------------------------------

    /** Queues the player's party (first, so it is ready at once) and PC. Reading the lists costs memory only; the store reads happen in {@link #sweepSome}. */
    private void enqueueSweep(net.minecraft.server.level.ServerPlayer player) {
        if (service() == null) return;
        var storage = Cobblemon.INSTANCE.getStorage();
        UUID id = player.getUUID();
        for (var pokemon : storage.getParty(player)) if (sweepQueue.size() < SWEEP_QUEUE_LIMIT) sweepQueue.add(new SweepJob(id, pokemon));
        for (var pokemon : storage.getPC(player)) if (sweepQueue.size() < SWEEP_QUEUE_LIMIT) sweepQueue.add(new SweepJob(id, pokemon));
    }

    /** Reconciles a bounded number of queued Pokemon each tick; reconcile is idempotent, so a Pokemon that moved meanwhile is harmless. */
    private void sweepSome(MinecraftServer s) {
        var service = service();
        if (service == null) { sweepQueue.clear(); return; }
        for (int i = 0; i < SWEEP_PER_TICK && !sweepQueue.isEmpty(); i++) {
            var job = sweepQueue.poll();
            safely("login reconcile", () -> service.reconcile(job.pokemon()));
        }
    }

    // --- acquisition -----------------------------------------------------------------------------------

    private void queue(Pokemon pokemon, UUID owner, Origin origin, boolean rollRarity, int tier, MinecraftServer s) {
        if (service() == null) {
            LOG.error("Progression is disabled; no profile created for {}", pokemon.getUuid());
            return;
        }
        if (pending.size() >= 1024) {
            LOG.error("Acquisition queue full; profile not created for {}", pokemon.getUuid());
            return;
        }
        pending.putIfAbsent(pokemon.getUuid(), new PendingAcquisition(pokemon, owner, origin, rollRarity, tier, s, s.getTickCount()));
    }

    private void finishAcquisitions(MinecraftServer s) {
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var item = iterator.next().getValue();
            if (item.server() != s) continue;
            if (!item.owner().equals(item.pokemon().getOwnerUUID())) {
                // Await explicit ownership; a timeout never treats an unsuccessful capture as owned.
                if (s.getTickCount() - item.queuedTick() >= 200) {
                    LOG.warn("No confirmed ownership for Pokemon {}; no profile assigned", item.pokemon().getUuid());
                    iterator.remove();
                }
                continue;
            }
            iterator.remove();
            var service = service();
            if (service == null) continue;
            try {
                var profile = service.acquire(item.pokemon(), item.origin(), item.rollRarity(), item.tier());
                LOG.info("acquired {} Pokemon {} ({}): {} slots {}", item.origin().kind(), item.pokemon().getUuid(),
                        profile.rarity().id(), profile.ordinarySlots().size(),
                        profile.ordinarySlots().stream().map(x -> x.slotId() + "=" + x.affixId() + "@" + x.rolledValue()).toList());
                var player = s.getPlayerList().getPlayer(item.owner());
                if (player != null) {
                    // A wild capture gets the card reveal if the client has the channel; everyone else (and hatches) keep the chat line.
                    boolean revealed = item.rollRarity() && com.ascensionlib.scout.CaptureRevealNet.send(player, item.pokemon(), service, profile);
                    player.sendSystemMessage(Component.literal(revealed
                            ? "[Ascend] " + title(profile) + " ascension assigned. /ascend inspect"
                            : "[Ascend] " + title(profile) + " ascension assigned. /ascend inspect — battle effects need CobbleRaids installed (untested live)."));
                }
            } catch (RuntimeException exception) {
                LOG.error("Acquisition failed for Pokemon {}; existing data preserved", item.pokemon().getUuid(), exception);
            }
        }
    }

    // --- commands --------------------------------------------------------------------------------------

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("ascend")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("inspect")
                        .executes(ctx -> inspect(ctx.getSource(), 1))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> inspect(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                .then(Commands.literal("wallet").executes(ctx -> wallet(ctx.getSource()))
                        .then(Commands.literal("gui").executes(ctx -> walletGui(ctx.getSource())))
                        // /ascend wallet withdraw <material> <amount>: turn wallet balance into tradable voucher items.
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("material", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Stream.of(MaterialId.values()).map(MaterialId::id), builder))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, (int) com.cobbleascend.store.ProgressionStore.MAX_MINT))
                                                .executes(ctx -> walletWithdraw(ctx.getSource(), StringArgumentType.getString(ctx, "material"),
                                                        IntegerArgumentType.getInteger(ctx, "amount"))))))
                        // /ascend wallet deposit [material]: redeem the voucher items you carry.
                        .then(Commands.literal("deposit").executes(ctx -> walletDeposit(ctx.getSource(), null))
                                .then(Commands.argument("material", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Stream.of(MaterialId.values()).map(MaterialId::id), builder))
                                        .executes(ctx -> walletDeposit(ctx.getSource(), StringArgumentType.getString(ctx, "material"))))))
                // /ascend craft [slot]: open the upgrade / refine / reforge screen for a party Pokemon (slot 1 by default).
                // /ascend ui [percent]: how big your Ascension pages are, 60 to 160 percent of the automatic fit (100 is the default).
                // /ascend dex: get an AscensionDex (right-click it to review your party); /ascend party opens the same page directly.
                // /ascend guide (or help): the in-game guide, the same text as the wiki.
                .then(Commands.literal("guide").executes(ctx -> openGuide(ctx.getSource())))
                .then(Commands.literal("help").executes(ctx -> openGuide(ctx.getSource())))
                .then(Commands.literal("dex").executes(ctx -> dex(ctx.getSource())))
                .then(Commands.literal("party").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    return com.ascensionlib.dex.PartyWeb.open(player) ? 1 : 0;
                }))
                .then(Commands.literal("ui")
                        .executes(ctx -> uiSize(ctx.getSource(), -1))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(com.ascensionlib.web.UiPrefs.MIN_PERCENT, com.ascensionlib.web.UiPrefs.MAX_PERCENT))
                                .executes(ctx -> uiSize(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "percent")))))
                .then(Commands.literal("craft")
                        .executes(ctx -> craft(ctx.getSource(), 1))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> craft(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                // /ascend fuse [slot]: open the fusion screen with a party Pokemon as the host (slot 1 by default).
                .then(Commands.literal("fuse")
                        .executes(ctx -> fuse(ctx.getSource(), 1))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> fuse(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                // /ascend sigil <slot>: spend one Ascension Sigil to give a party Pokemon that has no profile one (rarity rolled).
                .then(Commands.literal("sigil")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> sigil(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                .then(Commands.literal("scout")
                        .executes(ctx -> scoutList(ctx.getSource()))
                        .then(Commands.literal("look").executes(ctx -> scoutLook(ctx.getSource())))
                        .then(Commands.literal("use")
                                .then(Commands.argument("enemy", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> scoutUse(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "enemy"))))))
                .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("initialize")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> initialize(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                        // /ascend admin backup: write a dated snapshot of the progression store now (the mod also takes one at start and daily).
                        .then(Commands.literal("backup").executes(ctx -> backupNow(ctx.getSource())))
                        // /ascend admin reload: re-read config/ascensionlib.json (the economy's dials) without a restart, and say what changed.
                        .then(Commands.literal("reload").executes(ctx -> reloadConfig(ctx.getSource())))
                        .then(Commands.literal("rate-target").executes(ctx -> rateTarget(ctx.getSource())))
                        // Test seams for the capture reveal: re-send a real profile, or register a profile-less party Pokemon at a fixed rarity.
                        .then(Commands.literal("reveal")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> reveal(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"), null))))
                        .then(Commands.literal("acquire")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                .executes(ctx -> reveal(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"),
                                                        StringArgumentType.getString(ctx, "rarity"))))))
                        // /ascend admin attunement <slot> <points>: add lifetime attunement to one of your own party Pokemon.
                        .then(Commands.literal("attunement")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .then(Commands.argument("points", IntegerArgumentType.integer(1, 10_000))
                                                .executes(ctx -> grantAttunement(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"),
                                                        IntegerArgumentType.getInteger(ctx, "points"))))))
                        // /ascend admin completion <tier> [pay]: show the Tower completion reveal for a tier; with "pay" it settles a real chest for you.
                        .then(Commands.literal("completion")
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("bronze", "silver", "gold", "mythic"), builder))
                                        .executes(ctx -> completionDemo(ctx.getSource(), StringArgumentType.getString(ctx, "tier"), false))
                                        .then(Commands.literal("pay")
                                                .executes(ctx -> completionDemo(ctx.getSource(), StringArgumentType.getString(ctx, "tier"), true)))))
                        .then(Commands.literal("grant")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("material", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Stream.of(MaterialId.values()).map(MaterialId::id), builder))
                                                .then(Commands.argument("amount", LongArgumentType.longArg(1, 1_000_000))
                                                        .executes(ctx -> grant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "material"),
                                                                LongArgumentType.getLong(ctx, "amount"))))))));
    }

    private int status(CommandSourceStack source) {
        var status = AscensionApi.status();
        var service = service();
        if (service == null) {
            source.sendFailure(Component.literal("CobbleAscend progression is DISABLED: " + status.disabledReason()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("CobbleAscend: canonical progression store active (authority "
                + status.authority() + "). Capture/hatch rarity, level milestones and inspection work. "
                + "Battle effects need CobbleRaids installed and are untested live; crafting screens and Trials are not implemented."), false);
        if (!service.quarantined().isEmpty())
            source.sendFailure(Component.literal(service.quarantined().size()
                    + " Pokemon are quarantined (data preserved untouched); see the server log."));
        return 1;
    }

    private boolean ready(CommandSourceStack source) {
        if (service() != null) return true;
        source.sendFailure(Component.literal("CobbleAscend progression is disabled on this world; see /ascend status."));
        return false;
    }

    private int inspect(CommandSourceStack source, int slot) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var service = service();
        var player = source.getPlayerOrException();
        var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) {
            source.sendFailure(Component.literal("That party slot is empty."));
            return 0;
        }
        try {
            var profile = service.reconcile(pokemon);
            if (profile.isEmpty()) {
                source.sendFailure(Component.literal(service.quarantined().contains(pokemon.getUuid())
                        ? "This Pokemon's ascension data needs administrator review. It was preserved untouched."
                        : "No ascension profile. Pokemon you catch or hatch get one; for one you already had, use an Ascension Sigil (or an operator can initialize it as Common)."));
                return 0;
            }
            // A client with WebGUI gets the inspection page; anyone else keeps the text lines.
            if (com.ascensionlib.web.WebUi.canShow(player)) {
                com.ascensionlib.scout.InspectWeb.open(player, com.ascensionlib.scout.InspectWeb.Kind.OWNED, pokemon.getUuid().toString());
                return 1;
            }
            describe(source, service.rules(), slot, profile.get());
            return 1;
        } catch (RuntimeException exception) {
            LOG.warn("Unable to read profile for {}", pokemon.getUuid(), exception);
            source.sendFailure(Component.literal("Ascension data could not be read. Existing data was preserved."));
            return 0;
        }
    }

    private void describe(CommandSourceStack source, RankedRules rules, int slot, ProfileV1 profile) {
        String unique = profile.unique() == null ? "" : " · Unique: " + rules.unique(profile.unique().uniqueId())
                .map(u -> u.name()).orElse(profile.unique().uniqueId());
        source.sendSuccess(() -> Component.literal("Slot " + slot + " · " + title(profile) + " · revision " + profile.revision()
                + " · " + profile.pendingCredits() + " upgrade(s) pending · attunement " + profile.attunement() + unique
                + " · COMBAT EFFECTS INACTIVE"), false);
        for (var slotData : profile.ordinarySlots()) {
            var affix = rules.affix(slotData.affixId());
            var band = affix.band(slotData.rank());
            String parameter = slotData.type() == null ? "" : " (" + slotData.type() + ")";
            source.sendSuccess(() -> Component.literal(slotData.slotId() + " · " + affix.base().name() + " " + ROMAN[slotData.rank()]
                    + parameter + ": " + slotData.rolledValue() + "% [" + band.min() + "–" + band.max() + "%] · "
                    + affix.base().condition()), false);
        }
    }

    private int sigil(CommandSourceStack source, int slot) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var service = service();
        var player = source.getPlayerOrException();
        var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) {
            source.sendFailure(Component.literal("That party slot is empty."));
            return 0;
        }
        try {
            var profile = service.useSigil(player, pokemon);
            LOG.info("Player {} used an Ascension Sigil on Pokemon {}: {}", player.getUUID(), pokemon.getUuid(), profile.rarity().id());
            boolean revealed = com.ascensionlib.scout.CaptureRevealNet.send(player, pokemon, service, profile);
            source.sendSuccess(() -> Component.literal("[Ascend] The Sigil is spent: slot " + slot + " is now " + title(profile)
                    + (revealed ? "." : ". /ascend inspect " + slot)), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal("Sigil not used: " + exception.getMessage()));
            return 0;
        } catch (RuntimeException exception) {
            LOG.error("Sigil use failed for {}", pokemon.getUuid(), exception);
            source.sendFailure(Component.literal("The Sigil could not be used right now. You were not charged."));
            return 0;
        }
    }

    private int walletGui(CommandSourceStack source) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var player = source.getPlayerOrException();
        if (!com.ascensionlib.wallet.WalletWeb.open(player)) return 0;
        return 1;
    }

    private int walletWithdraw(CommandSourceStack source, String materialId, int amount) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        MaterialId material;
        try { material = MaterialId.fromId(materialId); } catch (IllegalArgumentException unknown) {
            source.sendFailure(Component.literal("Unknown material. Try " + Stream.of(MaterialId.values()).map(MaterialId::id).toList()));
            return 0;
        }
        var result = com.ascensionlib.wallet.WalletItems.withdraw(source.getPlayerOrException(), material, amount);
        if (result.ok()) source.sendSuccess(() -> Component.literal("[Wallet] " + result.message()), false);
        else source.sendFailure(Component.literal("[Wallet] " + result.message()));
        return result.ok() ? 1 : 0;
    }

    private int walletDeposit(CommandSourceStack source, String materialId) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        java.util.Optional<MaterialId> only = java.util.Optional.empty();
        if (materialId != null) {
            try { only = java.util.Optional.of(MaterialId.fromId(materialId)); } catch (IllegalArgumentException unknown) {
                source.sendFailure(Component.literal("Unknown material."));
                return 0;
            }
        }
        var result = com.ascensionlib.wallet.WalletItems.deposit(source.getPlayerOrException(), only);
        if (result.ok()) source.sendSuccess(() -> Component.literal("[Wallet] " + result.message()), false);
        else source.sendFailure(Component.literal("[Wallet] " + result.message()));
        return result.ok() ? 1 : 0;
    }

    private int wallet(CommandSourceStack source) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var service = service();
        var wallet = service.store().wallet(source.getPlayerOrException().getUUID());
        var text = new StringBuilder("Materials (wallet revision " + wallet.revision() + "):");
        for (var id : MaterialId.values()) text.append(' ').append(id.id()).append('=').append(wallet.balance(id));
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    /** The text fallback for the scouting screen: what the player can scout now, and what they have already seen. */
    private int scoutList(CommandSourceStack source) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var state = com.ascensionlib.scout.ScoutEncounters.stateFor(source.getPlayerOrException().getUUID());
        if (state.entries().isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Scout] No encounter to scout. Scouters: " + state.scouters()), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("[Scout] Scouters: " + state.scouters()
                + ". /ascend scout use <number> reveals one enemy."), false);
        for (int i = 0; i < state.entries().size(); i++) {
            var entry = state.entries().get(i);
            var text = new StringBuilder((i + 1) + ". " + entry.name());
            entry.detail().ifPresentOrElse(detail -> {
                text.append(" Lv.").append(detail.level()).append(" ").append(detail.rarityId()).append(" ")
                        .append(detail.types());
                if (!detail.uniqueName().isEmpty()) text.append(" Unique: ").append(detail.uniqueName());
                detail.slots().forEach(slot -> text.append("\n   ").append(slot.category()).append(": ")
                        .append(slot.name()).append(" rank ").append(slot.rank()).append(" (").append(slot.value()).append(")"));
            }, () -> text.append(" ?"));
            source.sendSuccess(() -> Component.literal(text.toString()), false);
        }
        return 1;
    }

    /**
     * Scouts the wild Pokemon the player is looking at: spends one Scouter and shows its rating, the one it will
     * have in a battle and when caught. Opens the inspect page for a client with WebGUI, else prints the lines.
     */
    private int scoutLook(CommandSourceStack source) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var player = source.getPlayerOrException();
        var target = com.ascensionlib.scout.WildTargeting.lookedAt(player);
        if (target == null) {
            source.sendFailure(Component.literal("[Scout] No Pokémon in view within " + (int) com.ascensionlib.scout.WildTargeting.RANGE + " blocks."));
            return 0;
        }
        var declared = com.ascensionlib.scout.ScoutEncounters.declareWild(player, target);
        if (!declared.ok()) {
            source.sendFailure(Component.literal("[Scout] " + declared.message()));
            return 0;
        }
        var message = com.ascensionlib.scout.ScoutEncounters.use(player.getUUID(), declared.encounterId(), declared.subjectId());
        source.sendSuccess(() -> Component.literal("[Scout] " + message), false);
        if (com.ascensionlib.web.WebUi.canShow(player)) {
            com.ascensionlib.scout.InspectWeb.open(player, com.ascensionlib.scout.InspectWeb.Kind.WILD, "");
        } else {
            var state = com.ascensionlib.scout.ScoutEncounters.wildStateFor(player.getUUID());
            for (var entry : state.entries()) {
                if (!entry.subjectId().equals(declared.subjectId())) continue;
                entry.detail().ifPresent(detail -> {
                    var text = new StringBuilder(entry.name() + " Lv." + detail.level() + " " + detail.rarityId() + (detail.resonance().isEmpty() ? "" : " " + detail.resonance()));
                    detail.slots().forEach(slot -> text.append("\n   ").append(slot.category()).append(": ").append(slot.name())
                            .append(" rank ").append(slot.rank()).append(" (").append(slot.value()).append("%)"));
                    source.sendSuccess(() -> Component.literal(text.toString()), false);
                });
            }
        }
        return 1;
    }

    private int scoutUse(CommandSourceStack source, int number) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var player = source.getPlayerOrException();
        var state = com.ascensionlib.scout.ScoutEncounters.stateFor(player.getUUID());
        if (number > state.entries().size()) {
            source.sendFailure(Component.literal("[Scout] There is no enemy " + number + ". /ascend scout lists them."));
            return 0;
        }
        var message = com.ascensionlib.scout.ScoutEncounters.use(player.getUUID(), state.encounterId(),
                state.entries().get(number - 1).subjectId());
        source.sendSuccess(() -> Component.literal("[Scout] " + message), false);
        return 1;
    }

    private int craft(CommandSourceStack source, int slot) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var player = source.getPlayerOrException();
        var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) {
            source.sendFailure(Component.literal("That party slot is empty."));
            return 0;
        }
        return com.ascensionlib.craft.CraftNet.openFor(player, pokemon) ? 1 : 0;
    }

    private int fuse(CommandSourceStack source, int slot) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var player = source.getPlayerOrException();
        var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) {
            source.sendFailure(Component.literal("That party slot is empty."));
            return 0;
        }
        if (!com.ascensionlib.fusion.FusionWeb.available()) {
            source.sendFailure(Component.literal("The fusion screen is a web page: install the WebGUI mod on the server."));
            return 0;
        }
        com.ascensionlib.fusion.FusionNet.openFor(player, pokemon);
        return 1;
    }

    private int reveal(CommandSourceStack source, int slot, String rarity) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var service = service();
        var player = source.getPlayerOrException();
        var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) {
            source.sendFailure(Component.literal("That party slot is empty."));
            return 0;
        }
        try {
            if (rarity != null) service.acquireWithRarity(pokemon, Origin.of("admin"), com.cobbleascend.domain.Rarity.fromId(rarity));
            var profile = service.canonical(pokemon);
            if (profile.isEmpty()) {
                source.sendFailure(Component.literal("That Pokemon has no ascension profile; use /ascend admin acquire <slot> <rarity>."));
                return 0;
            }
            boolean sent = com.ascensionlib.scout.CaptureRevealNet.send(player, pokemon, service, profile.get());
            source.sendSuccess(() -> Component.literal(sent ? "Reveal sent for slot " + slot + " (" + title(profile.get()) + ")."
                    : "Your client does not have the capture reveal channel."), false);
            return sent ? 1 : 0;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Refused: " + exception.getMessage()));
            return 0;
        }
    }

    private int openGuide(CommandSourceStack source) throws CommandSyntaxException {
        return com.ascensionlib.web.WebUi.open(source.getPlayerOrException(), "guide.html") ? 1 : 0;
    }

    private int dex(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (com.ascensionlib.dex.DexItem.carries(player)) {
            source.sendSuccess(() -> Component.literal("[Ascend] You already carry an AscensionDex. Right-click it to review your party."), false);
            return 1;
        }
        com.ascensionlib.dex.DexItem.give(player);
        source.sendSuccess(() -> Component.literal("[Ascend] Here is your AscensionDex. Right-click it to review your party."), false);
        return 1;
    }

    private int reloadConfig(CommandSourceStack source) {
        var before = com.cobbleascend.domain.v1.Tunables.current();
        var result = TunablesConfig.loadAndUse(TunablesConfig.file());
        var changed = TunablesConfig.differences(before, result.tunables());
        source.sendSuccess(() -> Component.literal("[Ascend] Reloaded config/ascensionlib.json: " + (changed.isEmpty() ? "nothing changed." : changed.size() + " setting(s) changed.")), true);
        changed.forEach(line -> source.sendSuccess(() -> Component.literal("  " + line), false));
        result.problems().forEach(problem -> source.sendFailure(Component.literal("  problem: " + problem)));
        return result.problems().isEmpty() ? 1 : 0;
    }

    private int backupNow(CommandSourceStack source) {
        var path = com.ascensionlib.BackupJob.now(source.getServer());
        if (path.isEmpty()) {
            source.sendFailure(Component.literal("[Ascend] The backup could not be made (see the server log), or progression is disabled."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[Ascend] Backed up the progression store to " + path.get() + " (the newest "
                + com.cobbleascend.store.StoreBackups.KEEP + " are kept)."), true);
        return 1;
    }

    private int uiSize(CommandSourceStack source, int percent) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (percent >= 0) {
            com.ascensionlib.web.UiPrefsWeb.of(player.getServer()).set(player.getUUID(), percent);
            com.ascensionlib.web.UiPrefsWeb.send(player);
        }
        int now = com.ascensionlib.web.UiPrefsWeb.percentOf(player);
        source.sendSuccess(() -> Component.literal("[Ascend] Your page size is " + now + "% (" + com.ascensionlib.web.UiPrefs.MIN_PERCENT + " to "
                + com.ascensionlib.web.UiPrefs.MAX_PERCENT + "; /ascend ui <percent> or the - and + in a page header)."), false);
        return 1;
    }

    private int completionDemo(CommandSourceStack source, String tierName, boolean pay) throws CommandSyntaxException {
        int cards = switch (tierName.toLowerCase(java.util.Locale.ROOT)) {
            case "bronze" -> 0; case "silver" -> 2; case "gold" -> 5; case "mythic" -> 8;
            default -> -1;
        };
        if (cards < 0) { source.sendFailure(Component.literal("Tier is bronze, silver, gold or mythic.")); return 0; }
        var player = source.getPlayerOrException();
        var encounter = java.util.UUID.randomUUID();
        if (pay) {
            var status = AscensionRewards.settleTowerCompletion(encounter, "VICTORY", 10, 0, cards, false, List.of(player.getUUID()));
            source.sendSuccess(() -> Component.literal("[Ascend] Settled a " + tierName + " completion: " + status.get(player.getUUID())), false);
            return 1;
        }
        var completion = com.cobbleascend.domain.v1.Tunables.current().towerCompletion().payout(encounter.toString(), player.getUUID(), cards, 0, false);
        com.ascensionlib.reward.CompletionReveal.show(player, com.ascensionlib.reward.CompletionReveal.view(completion, 0, cards, false, 3, true));
        return 1;
    }

    private int initialize(CommandSourceStack source, int slot) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var service = service();
        var player = source.getPlayerOrException();
        var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) {
            source.sendFailure(Component.literal("That party slot is empty."));
            return 0;
        }
        try {
            var profile = service.acquire(pokemon, Origin.of("admin"), false);
            LOG.info("Operator {} initialized ascension for owned Pokemon {} (existing profile preserved)", player.getUUID(), pokemon.getUuid());
            source.sendSuccess(() -> Component.literal("Slot " + slot + " now has a " + title(profile) + " profile."), true);
            return 1;
        } catch (RuntimeException exception) {
            LOG.warn("Initialization refused for {}", pokemon.getUuid(), exception);
            source.sendFailure(Component.literal("Initialization refused: " + exception.getMessage()));
            return 0;
        }
    }

    private int grantAttunement(CommandSourceStack source, int slot, int points) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var player = source.getPlayerOrException();
        var pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) {
            source.sendFailure(Component.literal("That party slot is empty."));
            return 0;
        }
        try {
            var profile = service().grantAttunement(pokemon, points);
            LOG.info("Operator {} granted {} attunement to Pokemon {} (now {})", player.getUUID(), points, pokemon.getUuid(), profile.attunement());
            source.sendSuccess(() -> Component.literal("Slot " + slot + " gained " + points + " attunement (now " + profile.attunement() + ")."), true);
            return 1;
        } catch (RuntimeException exception) {
            LOG.warn("Attunement grant refused for {}", pokemon.getUuid(), exception);
            source.sendFailure(Component.literal("Attunement grant refused: " + exception.getMessage()));
            return 0;
        }
    }

    /**
     * Operator test aid: previews the rating of the wild Pokemon the player is looking at and logs its UUID, so a
     * following capture can be compared (the capture logs the same UUID and rating).
     */
    private int rateTarget(CommandSourceStack source) throws CommandSyntaxException {
        if (!ready(source)) return 0;
        var service = service();
        var rules = service.rules();
        var player = source.getPlayerOrException();
        var from = player.getEyePosition();
        var to = from.add(player.getViewVector(1.0F).scale(32.0));
        PokemonEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var entity : player.level().getEntitiesOfClass(PokemonEntity.class,
                player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0))) {
            var hit = entity.getBoundingBox().inflate(0.3).clip(from, to);
            if (hit.isEmpty()) continue;
            double distance = from.distanceToSqr(hit.get());
            if (distance < bestDistance) { best = entity; bestDistance = distance; }
        }
        if (best == null) {
            source.sendFailure(Component.literal("No Pokemon in view within 32 blocks."));
            return 0;
        }
        var pokemon = best.getPokemon();
        try {
            var rating = service.previewWild(pokemon, Math.max(1, ProfileZones.tierOf(best.level().dimension().location())));
            var text = new StringBuilder(pokemon.getSpecies().getName() + " Lv" + pokemon.getLevel() + " · " + pokemon.getUuid()
                    + " · would be " + rating.rarity().id() + ", " + rating.upgradesOnCapture() + " upgrade(s) on capture:");
            for (var slotData : rating.slots()) {
                var affix = rules.affix(slotData.affixId());
                text.append("\n").append(slotData.slotId()).append(" · ").append(affix.base().name()).append(' ')
                        .append(ROMAN[slotData.rank()]).append(slotData.type() == null ? "" : " (" + slotData.type() + ")")
                        .append(": ").append(slotData.rolledValue()).append('%');
            }
            LOG.info("rate-target {}", text);
            source.sendSuccess(() -> Component.literal(text.toString()), false);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Preview refused: " + exception.getMessage()));
            return 0;
        }
    }

    private int grant(CommandSourceStack source, net.minecraft.server.level.ServerPlayer target, String material, long amount) {
        if (!ready(source)) return 0;
        var service = service();
        try {
            var id = MaterialId.fromId(material);
            var outcome = service.store().grant(UUID.randomUUID(), target.getUUID(), Map.of(id, amount), "operator grant by " + source.getTextName());
            LOG.info("Operator {} granted {} x{} to {} (wallet revision {})", source.getTextName(), id.id(), amount,
                    target.getUUID(), outcome.wallet().revision());
            source.sendSuccess(() -> Component.literal("Granted " + amount + " " + id.id() + " to " + target.getGameProfile().getName() + "."), true);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Grant refused: " + exception.getMessage()));
            return 0;
        }
    }

    private static String title(ProfileV1 profile) {
        String id = profile.rarity().id();
        String name = id.equals("mythical") ? "Mythic" : Character.toUpperCase(id.charAt(0)) + id.substring(1);
        return profile.unique() == null ? name : name + " · Unique";
    }
}
