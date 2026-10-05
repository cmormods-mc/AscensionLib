package com.ascensionlib;

import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.Origin;
import com.cobbleascend.domain.v1.ProfileV1;
import com.cobbleascend.domain.v1.RankedRules;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.HatchEggEvent;
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
    private MinecraftServer server;

    private record PendingAcquisition(Pokemon pokemon, UUID owner, Origin origin, boolean rollRarity,
                                      MinecraftServer server, int queuedTick) {}

    void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stop);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joined) -> joined.execute(() -> {
            var service = service();
            if (service != null) {
                int handled = service.sweep(handler.getPlayer());
                if (handled > 0) LOG.debug("Reconciled {} Pokemon for {}", handled, handler.getPlayer().getGameProfile().getName());
            }
        }));
        CobblemonEvents.POKEMON_CAPTURED.subscribe((Consumer<PokemonCapturedEvent>) event -> {
            var s = event.getPlayer().getServer();
            if (s != null) s.execute(() -> queue(event.getPokemon(), event.getPlayer().getUUID(), Origin.of("wild_capture"), true, s));
        });
        CobblemonEvents.HATCH_EGG_POST.subscribe((Consumer<HatchEggEvent.Post>) event -> {
            var s = event.getPlayer().getServer();
            if (s != null) s.execute(() -> queue(event.getPokemon(), event.getPlayer().getUUID(), Origin.of("hatch"), false, s));
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(buildCommand()));
        LOG.info("Capture rarity, level milestones and /ascend inspection enabled. Battle effects act only when CobbleRaids is installed (they are untested in a live battle); crafting and Trials are NOT active.");
    }

    private void stop(MinecraftServer stopping) {
        pending.entrySet().removeIf(e -> e.getValue().server() == stopping);
        server = null;
    }

    /** The library's service for the running world, or null while there is none or progression is disabled. */
    private static ProfileService service() { return AscensionApi.service().orElse(null); }

    private void safely(String what, Runnable action) {
        try { action.run(); } catch (RuntimeException exception) { LOG.error("{} failed; data preserved", what, exception); }
    }

    // --- acquisition -----------------------------------------------------------------------------------

    private void queue(Pokemon pokemon, UUID owner, Origin origin, boolean rollRarity, MinecraftServer s) {
        if (service() == null) {
            LOG.error("Progression is disabled; no profile created for {}", pokemon.getUuid());
            return;
        }
        if (pending.size() >= 1024) {
            LOG.error("Acquisition queue full; profile not created for {}", pokemon.getUuid());
            return;
        }
        pending.putIfAbsent(pokemon.getUuid(), new PendingAcquisition(pokemon, owner, origin, rollRarity, s, s.getTickCount()));
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
                var profile = service.acquire(item.pokemon(), item.origin(), item.rollRarity());
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
                .then(Commands.literal("wallet").executes(ctx -> wallet(ctx.getSource())))
                // /ascend craft [slot]: open the upgrade / refine / reforge screen for a party Pokemon (slot 1 by default).
                .then(Commands.literal("craft")
                        .executes(ctx -> craft(ctx.getSource(), 1))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> craft(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                .then(Commands.literal("scout")
                        .executes(ctx -> scoutList(ctx.getSource()))
                        .then(Commands.literal("use")
                                .then(Commands.argument("enemy", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> scoutUse(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "enemy"))))))
                .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("initialize")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> initialize(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
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
                        : "No ascension profile. New captures and hatches get one; an operator can initialize an existing Pokemon as Common."));
                return 0;
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
        if (!com.ascensionlib.craft.CraftNet.sendView(player, pokemon, true, "")) {
            source.sendFailure(Component.literal("That Pokemon has no ascension profile, or your client does not have the upgrade screen."));
            return 0;
        }
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
            var rating = service.previewWild(pokemon);
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
