package com.ascensionlib.craft;

import com.ascensionlib.AscensionApi;
import com.ascensionlib.ProfileService;
import com.cobbleascend.domain.Rarity;
import com.cobbleascend.domain.v1.Category;
import com.cobbleascend.domain.v1.CraftException;
import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.OrdinarySlot;
import com.cobbleascend.domain.v1.ProfileV1;
import com.cobbleascend.store.CraftRequest;
import com.cobbleascend.store.Kind;
import com.cobbleascend.store.StoreException;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server half of the upgrade / refine / reforge screen. {@link CraftPayloads.Open} only reads; {@link CraftPayloads.Confirm} is the one
 * way a craft happens, and it passes through {@link ProfileService#craft} so the store's operation id, revision and wallet checks
 * decide it. The player must own the Pokemon (party or PC) both times, and never sees another player's.
 */
public final class CraftNet {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");

    private CraftNet() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(CraftPayloads.Open.TYPE, CraftPayloads.Open.CODEC);
        PayloadTypeRegistry.playC2S().register(CraftPayloads.Confirm.TYPE, CraftPayloads.Confirm.CODEC);
        PayloadTypeRegistry.playS2C().register(CraftPayloads.View.TYPE, CraftPayloads.View.CODEC);
        PayloadTypeRegistry.playS2C().register(CraftPayloads.Done.TYPE, CraftPayloads.Done.CODEC);
        // Fabric already runs receivers on the server thread; the extra execute() only defers the work, which is harmless.
        ServerPlayNetworking.registerGlobalReceiver(CraftPayloads.Open.TYPE, (payload, context) -> context.server().execute(() -> {
            var player = context.player();
            // Each open scans the player's party and PC and reads the wallet: a client must not be able to spam it.
            if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "craft.open", 6, 3)) return;
            long profiled = com.ascensionlib.Profiler.start();
            try {
                findOwned(player, payload.pokemonId()).ifPresent(p -> {
                    var locked = com.ascensionlib.CraftLocks.reason(p);
                    if (locked.isPresent()) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(locked.get()));
                    else sendView(player, p, false, "");
                });
            } catch (RuntimeException exception) {
                LOG.error("Craft open failed for {}", player.getGameProfile().getName(), exception);
            }
            com.ascensionlib.Profiler.stop("server.craftOpen", profiled);
        }));
        ServerPlayNetworking.registerGlobalReceiver(CraftPayloads.Confirm.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    long profiled = com.ascensionlib.Profiler.start();
                    confirm(context.player(), payload);
                    com.ascensionlib.Profiler.stop("server.craftConfirm", profiled);
                }));
    }

    /** The player's own Pokemon in party or PC with this id, never a battle clone. */
    public static Optional<Pokemon> findOwned(ServerPlayer player, String pokemonId) {
        UUID id;
        try { id = UUID.fromString(pokemonId); } catch (IllegalArgumentException e) { return Optional.empty(); }
        long profiled = com.ascensionlib.Profiler.start();
        List<Pokemon> all = new ArrayList<>();
        Cobblemon.INSTANCE.getStorage().getParty(player).forEach(all::add);
        Cobblemon.INSTANCE.getStorage().getPC(player).forEach(all::add);
        var found = all.stream().filter(p -> p.getUuid().equals(id) && !p.isBattleClone() && player.getUUID().equals(p.getOwnerUUID())).findFirst();
        com.ascensionlib.Profiler.stop("server.findOwned", profiled);
        return found;
    }

    public static boolean sendView(ServerPlayer player, Pokemon pokemon, boolean open, String message) {
        var service = AscensionApi.service().orElse(null);
        if (service == null || !ServerPlayNetworking.canSend(player, CraftPayloads.View.TYPE)) return false;
        if (com.ascensionlib.CraftLocks.locked(pokemon)) return false;     // a lent Pokemon has no upgrade screen
        long profiled = com.ascensionlib.Profiler.start();
        var profile = service.canonical(pokemon);
        com.ascensionlib.Profiler.stop("server.canonical", profiled);
        if (profile.isEmpty()) return false;
        profiled = com.ascensionlib.Profiler.start();
        var built = view(player, pokemon, service, profile.get(), open, message);
        com.ascensionlib.Profiler.stop("server.craftView", profiled);
        ServerPlayNetworking.send(player, built);
        return true;
    }

    private static String slotName(ProfileService service, OrdinarySlot slot) {
        var affix = service.rules().affix(slot.affixId());
        return affix.base().name() + (slot.type() == null ? "" : " (" + slot.type() + ")");
    }

    static CraftPayloads.View view(ServerPlayer player, Pokemon pokemon, ProfileService service, ProfileV1 profile, boolean open, String message) {
        var rules = service.rules();
        var wallet = service.store().wallet(player.getUUID());
        var refineCost = rules.base().craftCost("refine");
        var reforgeCost = rules.base().craftCost("reforge");
        boolean canRefine = wallet.canAfford(MaterialId.of(refineCost));
        boolean canReforge = wallet.canAfford(MaterialId.of(reforgeCost));
        var types = service.typesFor(pokemon);
        List<CraftPayloads.SlotView> slots = new ArrayList<>();
        for (var slot : profile.ordinarySlots()) {
            var affix = rules.affix(slot.affixId());
            var band = affix.band(slot.rank());
            boolean top = slot.rank() >= 5;
            var next = top ? null : affix.band(slot.rank() + 1);
            String upgradeBlock = profile.pendingCredits() < 1 ? "No pending upgrade" : top ? "Already at the top rank" : "";
            String refineBlock = canRefine ? "" : "Needs " + describe(refineCost);
            String reforgeBlock = "";
            int pool = 0;
            try {
                service.preview(profile, Kind.REFORGE, slot.slotId(), types);
                var others = profile.ordinarySlots().stream().filter(s -> !s.slotId().equals(slot.slotId())).map(s -> rules.affix(s.affixId()).family()).toList();
                pool = (int) rules.affixes().stream().filter(a -> a.category() == slot.category() && !others.contains(a.family())).count();
                if (!canReforge) reforgeBlock = "Needs " + describe(reforgeCost);
            } catch (CraftException exception) {
                reforgeBlock = "No other affix can take this slot";
            }
            slots.add(new CraftPayloads.SlotView(slot.slotId(), slot.category().id(), slotName(service, slot),
                    affix.base().condition() == null ? "" : affix.base().condition(), slot.rank(), slot.rolledValue(), band.min(), band.max(),
                    next == null ? -1 : next.min(), next == null ? -1 : next.max(), upgradeBlock, refineBlock, reforgeBlock, pool));
        }
        CraftPayloads.Promotion promotion;
        if (profile.rarity() == Rarity.MYTHICAL) {
            promotion = new CraftPayloads.Promotion("", new CraftPayloads.Price(0, 0, 0), profile.attunement(), 0, "Already Mythical");
        } else {
            var step = rules.base().promotion(profile.rarity());
            String block = profile.attunement() < step.attunement()
                    ? "Needs " + step.attunement() + " attunement (has " + profile.attunement() + ")"
                    : !wallet.canAfford(MaterialId.of(step.cost())) ? "Needs " + describe(step.cost()) : "";
            promotion = new CraftPayloads.Promotion(step.to().id(), new CraftPayloads.Price(step.cost().dust(), step.cost().facets(),
                    step.cost().cores()), profile.attunement(), step.attunement(), block);
        }
        var held = profile.unique() == null ? "" : profile.unique().uniqueId();
        var uniqueOptions = new ArrayList<CraftPayloads.UniqueOption>();
        for (var definition : rules.uniques())
            uniqueOptions.add(new CraftPayloads.UniqueOption(definition.id(), definition.name(), definition.benefit(), definition.drawback(),
                    definition.id().equals(held)));
        long catalysts = wallet.balance(MaterialId.UNIQUE_CATALYST);
        var uniqueState = new CraftPayloads.UniqueState(held, catalysts, wallet.balance(MaterialId.UNIQUE_FRAGMENT),
                com.cobbleascend.store.StoreConfig.defaults().catalystFragments(), uniqueOptions,
                service.isFused(pokemon.getUuid()) ? "A Transcendent's power cannot be replaced" : catalysts < 1 ? "Needs 1 Unique Catalyst" : "");
        int nextMilestone = 0;
        for (int m = 10; m <= 100; m += 10) if (m > profile.highestLevelObserved()) { nextMilestone = m; break; }
        return new CraftPayloads.View(pokemon.getUuid().toString(), pokemon.getDisplayName(false).getString(),
                pokemon.getSpecies().getResourceIdentifier().toString(), new ArrayList<>(pokemon.getAspects()), pokemon.getLevel(),
                profile.rarity().id(), service.uniqueLabel(profile),
                profile.pendingCredits(), nextMilestone, profile.revision(), wallet.revision(), wallet.balance(MaterialId.RESONANCE_DUST),
                wallet.balance(MaterialId.FACET), wallet.balance(MaterialId.ASCENSION_CORE), wallet.balance(MaterialId.SCOUTER),
                new CraftPayloads.Price(refineCost.dust(), refineCost.facets(), refineCost.cores()),
                new CraftPayloads.Price(reforgeCost.dust(), reforgeCost.facets(), reforgeCost.cores()), promotion, uniqueState, slots, message, open);
    }

    static String describe(com.cobbleascend.domain.Cost cost) {
        List<String> parts = new ArrayList<>();
        if (cost.dust() > 0) parts.add(cost.dust() + " Resonance Dust");
        if (cost.facets() > 0) parts.add(cost.facets() + " Facet" + (cost.facets() == 1 ? "" : "s"));
        if (cost.cores() > 0) parts.add(cost.cores() + " Core" + (cost.cores() == 1 ? "" : "s"));
        return String.join(" + ", parts);
    }

    private static void confirm(ServerPlayer player, CraftPayloads.Confirm request) {
        // Every distinct operation id is a store transaction (an fsync): bound how fast one player can submit them.
        if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "craft.confirm", 4, 2)) {
            ServerPlayNetworking.send(player, new CraftPayloads.Done(request.operationId(), false, "Too many requests. Wait a moment.",
                    request.slotId(), "", 0, 0, "", 0, 0, false));
            return;
        }
        var service = AscensionApi.service().orElse(null);
        UUID operation;
        try { operation = UUID.fromString(request.operationId()); } catch (IllegalArgumentException e) { return; }
        if (service == null) { fail(player, request, "Progression is unavailable."); return; }
        var found = findOwned(player, request.pokemonId());
        if (found.isEmpty()) { fail(player, request, "That Pokémon is no longer yours to change."); return; }
        var pokemon = found.get();
        var locked = com.ascensionlib.CraftLocks.reason(pokemon);
        if (locked.isPresent()) { fail(player, request, locked.get()); return; }
        var before = service.canonical(pokemon);
        if (before.isEmpty()) { fail(player, request, "That Pokémon has no ascension profile."); return; }
        if (request.kind().equals("assemble")) { assemble(player, service, pokemon, request, operation); return; }
        if (request.kind().equals("unique") && service.isFused(pokemon.getUuid())) {
            fail(player, request, "A Transcendent's power cannot be replaced.");
            return;
        }
        CraftRequest craft;
        switch (request.kind()) {
            case "upgrade" -> craft = CraftRequest.upgrade(operation, player.getUUID(), pokemon.getUuid(), request.slotId(), request.profileRevision(),
                    CraftRequest.ANY_REVISION);
            case "refine" -> craft = CraftRequest.refine(operation, player.getUUID(), pokemon.getUuid(), request.slotId(), request.profileRevision(),
                    request.walletRevision());
            case "reforge" -> craft = CraftRequest.reforge(operation, player.getUUID(), pokemon.getUuid(), request.slotId(), service.typesFor(pokemon),
                    request.profileRevision(), request.walletRevision());
            case "unique" -> craft = before.get().unique() == null
                    ? CraftRequest.installUnique(operation, player.getUUID(), pokemon.getUuid(), request.slotId(), request.profileRevision(), request.walletRevision())
                    : CraftRequest.replaceUnique(operation, player.getUUID(), pokemon.getUuid(), request.slotId(), request.profileRevision(), request.walletRevision());
            case "promote" -> craft = CraftRequest.promote(operation, player.getUUID(), pokemon.getUuid(), service.typesFor(pokemon),
                    request.profileRevision(), request.walletRevision());
            default -> { fail(player, request, "Unknown action."); return; }
        }
        try {
            var outcome = service.craft(pokemon, craft);
            if (craft.kind() == Kind.INSTALL_UNIQUE || craft.kind() == Kind.REPLACE_UNIQUE) {
                var was = before.get().unique() == null ? "" : uniqueName(service, before.get().unique().uniqueId());
                var now = uniqueName(service, outcome.profile().unique().uniqueId());
                LOG.info("{} committed on Pokemon {} -> {} by {} (operation {}{})", craft.kind().name().toLowerCase(java.util.Locale.ROOT), pokemon.getUuid(),
                        outcome.profile().unique().uniqueId(), player.getGameProfile().getName(), operation, outcome.replayed() ? ", replayed" : "");
                ServerPlayNetworking.send(player, new CraftPayloads.Done(request.operationId(), true, "", request.slotId(), was, 0, 0, now, 0, 0, outcome.replayed()));
                sendView(player, pokemon, false, "");
                return;
            }
            if (craft.kind() == com.cobbleascend.store.Kind.PROMOTE) {
                LOG.info("promote committed on Pokemon {} {} -> {} by {} (operation {}{})", pokemon.getUuid(), before.get().rarity().id(),
                        outcome.profile().rarity().id(), player.getGameProfile().getName(), operation, outcome.replayed() ? ", replayed" : "");
                ServerPlayNetworking.send(player, new CraftPayloads.Done(request.operationId(), true, "", "", before.get().rarity().id(), 0, 0,
                        outcome.profile().rarity().id(), 0, 0, outcome.replayed()));
                sendView(player, pokemon, false, "");
                return;
            }
            var oldSlot = before.get().slot(request.slotId());
            var newSlot = outcome.profile() == null ? java.util.Optional.<OrdinarySlot>empty() : outcome.profile().slot(request.slotId());
            LOG.info("{} {} on Pokemon {} slot {} by {} (operation {}{})", request.kind(), "committed", pokemon.getUuid(), request.slotId(),
                    player.getGameProfile().getName(), operation, outcome.replayed() ? ", replayed" : "");
            ServerPlayNetworking.send(player, new CraftPayloads.Done(request.operationId(), true, "", request.slotId(),
                    oldSlot.map(s -> slotName(service, s)).orElse(""), oldSlot.map(OrdinarySlot::rank).orElse(0), oldSlot.map(OrdinarySlot::rolledValue).orElse(0),
                    newSlot.map(s -> slotName(service, s)).orElse(""), newSlot.map(OrdinarySlot::rank).orElse(0), newSlot.map(OrdinarySlot::rolledValue).orElse(0),
                    outcome.replayed()));
            sendView(player, pokemon, false, "");
        } catch (StoreException exception) {
            fail(player, request, switch (exception.code()) {
                case STALE_PROFILE, STALE_WALLET -> "This Pokémon or your materials changed. Review it again.";
                case OPERATION_REUSED -> "That confirmation was already used.";
                default -> "The change could not be made: " + exception.getMessage();
            });
        } catch (CraftException exception) {
            fail(player, request, switch (exception.reason()) {
                case NO_PENDING_CREDIT -> "No pending upgrade to spend.";
                case MAX_RANK -> "That slot is already at the top rank.";
                case INSUFFICIENT_FUNDS -> "You do not have enough materials.";
                case NO_ELIGIBLE_AFFIX -> "No other affix can take that slot.";
                case INSUFFICIENT_ATTUNEMENT -> "Not enough attunement to promote yet.";
                case UNIQUE_PRESENT, NO_UNIQUE, SAME_UNIQUE, UNKNOWN_UNIQUE -> "That Unique cannot be set on this Pokémon: " + exception.getMessage();
                case MAX_RARITY -> "Already at the highest rarity.";
                default -> "The change is not allowed: " + exception.getMessage();
            });
        } catch (RuntimeException exception) {
            LOG.error("Craft {} failed for {}", request.kind(), pokemon.getUuid(), exception);
            fail(player, request, "Something went wrong; nothing was spent.");
        }
    }

    private static String uniqueName(ProfileService service, String id) {
        return service.rules().unique(id).map(u -> u.name()).orElse(id);
    }

    /** Turns the player's Fragments into one Catalyst. Needs no Pokemon change; the view is refreshed so the new balance shows. */
    private static void assemble(ServerPlayer player, ProfileService service, Pokemon pokemon, CraftPayloads.Confirm request, UUID operation) {
        try {
            var outcome = service.store().assembleCatalyst(operation, player.getUUID(), request.walletRevision());
            LOG.info("assemble committed by {} (operation {}{})", player.getGameProfile().getName(), operation, outcome.replayed() ? ", replayed" : "");
            ServerPlayNetworking.send(player, new CraftPayloads.Done(request.operationId(), true, "", "", "", 0, 0, "Unique Catalyst", 0, 0, outcome.replayed()));
            sendView(player, pokemon, false, "");
        } catch (StoreException exception) {
            fail(player, request, exception.code() == StoreException.Code.STALE_WALLET
                    ? "Your materials changed. Review it again." : "The Catalyst could not be assembled: " + exception.getMessage());
        } catch (CraftException exception) {
            fail(player, request, exception.reason() == CraftException.Reason.INSUFFICIENT_FUNDS
                    ? "You do not have enough Unique Fragments." : "The Catalyst could not be assembled: " + exception.getMessage());
        } catch (RuntimeException exception) {
            LOG.error("Catalyst assembly failed for {}", player.getGameProfile().getName(), exception);
            fail(player, request, "Something went wrong; nothing was spent.");
        }
    }

    private static void fail(ServerPlayer player, CraftPayloads.Confirm request, String message) {
        ServerPlayNetworking.send(player, new CraftPayloads.Done(request.operationId(), false, message, request.slotId(), "", 0, 0, "", 0, 0, false));
        findOwned(player, request.pokemonId()).ifPresent(p -> sendView(player, p, false, message));
    }
}
