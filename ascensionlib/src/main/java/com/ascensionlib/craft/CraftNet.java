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
        ServerPlayNetworking.registerGlobalReceiver(CraftPayloads.Open.TYPE, (payload, context) -> context.server().execute(() -> {
            var player = context.player();
            findOwned(player, payload.pokemonId()).ifPresent(p -> sendView(player, p, false, ""));
        }));
        ServerPlayNetworking.registerGlobalReceiver(CraftPayloads.Confirm.TYPE, (payload, context) ->
                context.server().execute(() -> confirm(context.player(), payload)));
    }

    /** The player's own Pokemon in party or PC with this id, never a battle clone. */
    public static Optional<Pokemon> findOwned(ServerPlayer player, String pokemonId) {
        UUID id;
        try { id = UUID.fromString(pokemonId); } catch (IllegalArgumentException e) { return Optional.empty(); }
        List<Pokemon> all = new ArrayList<>();
        Cobblemon.INSTANCE.getStorage().getParty(player).forEach(all::add);
        Cobblemon.INSTANCE.getStorage().getPC(player).forEach(all::add);
        return all.stream().filter(p -> p.getUuid().equals(id) && !p.isBattleClone() && player.getUUID().equals(p.getOwnerUUID())).findFirst();
    }

    public static boolean sendView(ServerPlayer player, Pokemon pokemon, boolean open, String message) {
        var service = AscensionApi.service().orElse(null);
        if (service == null || !ServerPlayNetworking.canSend(player, CraftPayloads.View.TYPE)) return false;
        var profile = service.canonical(pokemon);
        if (profile.isEmpty()) return false;
        ServerPlayNetworking.send(player, view(player, pokemon, service, profile.get(), open, message));
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
        int nextMilestone = 0;
        for (int m = 10; m <= 100; m += 10) if (m > profile.highestLevelObserved()) { nextMilestone = m; break; }
        return new CraftPayloads.View(pokemon.getUuid().toString(), pokemon.getDisplayName(false).getString(),
                pokemon.getSpecies().getResourceIdentifier().toString(), new ArrayList<>(pokemon.getAspects()), pokemon.getLevel(),
                profile.rarity().id(), profile.unique() == null ? "" : rules.unique(profile.unique().uniqueId()).map(u -> u.name()).orElse("Unknown Unique"),
                profile.pendingCredits(), nextMilestone, profile.revision(), wallet.revision(), wallet.balance(MaterialId.RESONANCE_DUST),
                wallet.balance(MaterialId.FACET), wallet.balance(MaterialId.ASCENSION_CORE),
                new CraftPayloads.Price(refineCost.dust(), refineCost.facets(), refineCost.cores()),
                new CraftPayloads.Price(reforgeCost.dust(), reforgeCost.facets(), reforgeCost.cores()), promotion, slots, message, open);
    }

    static String describe(com.cobbleascend.domain.Cost cost) {
        List<String> parts = new ArrayList<>();
        if (cost.dust() > 0) parts.add(cost.dust() + " Resonance Dust");
        if (cost.facets() > 0) parts.add(cost.facets() + " Facet" + (cost.facets() == 1 ? "" : "s"));
        if (cost.cores() > 0) parts.add(cost.cores() + " Core" + (cost.cores() == 1 ? "" : "s"));
        return String.join(" + ", parts);
    }

    private static void confirm(ServerPlayer player, CraftPayloads.Confirm request) {
        var service = AscensionApi.service().orElse(null);
        UUID operation;
        try { operation = UUID.fromString(request.operationId()); } catch (IllegalArgumentException e) { return; }
        if (service == null) { fail(player, request, "Progression is unavailable."); return; }
        var found = findOwned(player, request.pokemonId());
        if (found.isEmpty()) { fail(player, request, "That Pokémon is no longer yours to change."); return; }
        var pokemon = found.get();
        var before = service.canonical(pokemon);
        if (before.isEmpty()) { fail(player, request, "That Pokémon has no ascension profile."); return; }
        CraftRequest craft;
        switch (request.kind()) {
            case "upgrade" -> craft = CraftRequest.upgrade(operation, player.getUUID(), pokemon.getUuid(), request.slotId(), request.profileRevision(),
                    CraftRequest.ANY_REVISION);
            case "refine" -> craft = CraftRequest.refine(operation, player.getUUID(), pokemon.getUuid(), request.slotId(), request.profileRevision(),
                    request.walletRevision());
            case "reforge" -> craft = CraftRequest.reforge(operation, player.getUUID(), pokemon.getUuid(), request.slotId(), service.typesFor(pokemon),
                    request.profileRevision(), request.walletRevision());
            case "promote" -> craft = CraftRequest.promote(operation, player.getUUID(), pokemon.getUuid(), service.typesFor(pokemon),
                    request.profileRevision(), request.walletRevision());
            default -> { fail(player, request, "Unknown action."); return; }
        }
        try {
            var outcome = service.craft(pokemon, craft);
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
                case MAX_RARITY -> "Already at the highest rarity.";
                default -> "The change is not allowed: " + exception.getMessage();
            });
        } catch (RuntimeException exception) {
            LOG.error("Craft {} failed for {}", request.kind(), pokemon.getUuid(), exception);
            fail(player, request, "Something went wrong; nothing was spent.");
        }
    }

    private static void fail(ServerPlayer player, CraftPayloads.Confirm request, String message) {
        ServerPlayNetworking.send(player, new CraftPayloads.Done(request.operationId(), false, message, request.slotId(), "", 0, 0, "", 0, 0, false));
        findOwned(player, request.pokemonId()).ifPresent(p -> sendView(player, p, false, message));
    }
}
