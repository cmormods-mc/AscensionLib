package com.ascensionlib.fusion;

import com.ascensionlib.AscensionApi;
import com.ascensionlib.ProfileService;
import com.ascensionlib.craft.CraftNet;
import com.cobbleascend.domain.Rarity;
import com.cobbleascend.domain.v1.CraftException;
import com.cobbleascend.domain.v1.FusionRules;
import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.Transcendence;
import com.cobbleascend.store.StoreException;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server half of the fusion screen. {@link FusionPayloads.Open} and {@link FusionPayloads.Preview} only read. A fusion happens only on a
 * {@link FusionPayloads.Confirm}, through {@link ProfileService#fuse}, where the store applies the rules, the price and the revisions in
 * one transaction. The player must own both Pokemon, and a lent (craft-locked) Pokemon is never offered.
 */
public final class FusionNet {
    private static final Logger LOG = LoggerFactory.getLogger("ascensionlib");

    private FusionNet() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(FusionPayloads.Open.TYPE, FusionPayloads.Open.CODEC);
        PayloadTypeRegistry.playC2S().register(FusionPayloads.Preview.TYPE, FusionPayloads.Preview.CODEC);
        PayloadTypeRegistry.playC2S().register(FusionPayloads.Confirm.TYPE, FusionPayloads.Confirm.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionPayloads.Candidates.TYPE, FusionPayloads.Candidates.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionPayloads.Detail.TYPE, FusionPayloads.Detail.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionPayloads.Result.TYPE, FusionPayloads.Result.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FusionPayloads.Open.TYPE, (payload, context) -> context.server().execute(() -> {
            var player = context.player();
            if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "fusion.open", 4, 2)) return;
            try { sendCandidates(player, payload.hostId()); }
            catch (RuntimeException exception) { LOG.error("Fusion open failed for {}", player.getGameProfile().getName(), exception); }
        }));
        ServerPlayNetworking.registerGlobalReceiver(FusionPayloads.Preview.TYPE, (payload, context) -> context.server().execute(() -> {
            var player = context.player();
            if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "fusion.preview", 8, 4)) return;
            try { sendDetail(player, payload.hostId(), payload.donorId()); }
            catch (RuntimeException exception) { LOG.error("Fusion preview failed for {}", player.getGameProfile().getName(), exception); }
        }));
        ServerPlayNetworking.registerGlobalReceiver(FusionPayloads.Confirm.TYPE, (payload, context) -> context.server().execute(() -> confirm(context.player(), payload)));
    }

    private static List<Pokemon> owned(ServerPlayer player) {
        List<Pokemon> all = new ArrayList<>();
        Cobblemon.INSTANCE.getStorage().getParty(player).forEach(all::add);
        Cobblemon.INSTANCE.getStorage().getPC(player).forEach(all::add);
        all.removeIf(p -> p.isBattleClone() || !player.getUUID().equals(p.getOwnerUUID()));
        return all;
    }

    private static boolean fused(ProfileService service, Pokemon pokemon) {
        return service.store().fusion(pokemon.getUuid()).isPresent() || service.store().consumedBy(pokemon.getUuid()).isPresent();
    }

    /** Why this pairing cannot be done now; empty when it can. */
    private static String blockFor(ServerPlayer player, ProfileService service, Pokemon host, Pokemon donor) {
        var hostProfile = service.canonical(host);
        var donorProfile = service.canonical(donor);
        if (hostProfile.isEmpty() || donorProfile.isEmpty()) return "A Pokémon has no ascension profile.";
        var locked = com.ascensionlib.CraftLocks.reason(host).or(() -> com.ascensionlib.CraftLocks.reason(donor));
        if (locked.isPresent()) return locked.get();
        try {
            FusionRules.check(hostProfile.get(), fused(service, host), donorProfile.get(), fused(service, donor), service.store().wallet(player.getUUID()));
            return "";
        } catch (CraftException exception) {
            return exception.getMessage();
        }
    }

    /** Sends the candidate list and asks the client to open the screen (a command did). */
    public static void openFor(ServerPlayer player, Pokemon host) {
        sendCandidates(player, host.getUuid().toString());
    }

    static void sendCandidates(ServerPlayer player, String hostId) {
        var service = AscensionApi.service().orElse(null);
        if (service == null || !ServerPlayNetworking.canSend(player, FusionPayloads.Candidates.TYPE)) return;
        var host = CraftNet.findOwned(player, hostId).orElse(null);
        if (host == null) return;
        var hostProfile = service.canonical(host);
        String hostBlock = hostProfile.isEmpty() ? "That Pokémon has no ascension profile."
                : hostProfile.get().rarity() != Rarity.MYTHICAL ? "Only a Mythic Pokémon can be the host."
                : hostProfile.get().unique() == null ? "The host needs a Unique." : fused(service, host) ? "This Pokémon is already a Transcendent." : "";
        var donors = new ArrayList<FusionPayloads.Candidate>();
        if (hostBlock.isEmpty()) {
            for (var other : owned(player)) {
                if (other.getUuid().equals(host.getUuid())) continue;
                var profile = service.canonical(other);
                if (profile.isEmpty() || profile.get().unique() == null || profile.get().rarity().ordinal() < Rarity.EPIC.ordinal() || fused(service, other)) continue;
                donors.add(new FusionPayloads.Candidate(other.getUuid().toString(), other.getDisplayName(false).getString(),
                        other.getSpecies().getResourceIdentifier().toString(), profile.get().rarity().id(),
                        service.rules().unique(profile.get().unique().uniqueId()).map(u -> u.name()).orElse(profile.get().unique().uniqueId()),
                        blockFor(player, service, host, other)));
                if (donors.size() >= 96) break;
            }
        }
        ServerPlayNetworking.send(player, new FusionPayloads.Candidates(host.getUuid().toString(), host.getDisplayName(false).getString(),
                host.getSpecies().getResourceIdentifier().toString(), hostBlock, donors));
    }

    static void sendDetail(ServerPlayer player, String hostId, String donorId) {
        var service = AscensionApi.service().orElse(null);
        if (service == null || !ServerPlayNetworking.canSend(player, FusionPayloads.Detail.TYPE)) return;
        var host = CraftNet.findOwned(player, hostId).orElse(null);
        var donor = CraftNet.findOwned(player, donorId).orElse(null);
        if (host == null || donor == null || host == donor) return;
        var hostProfile = service.canonical(host);
        var donorProfile = service.canonical(donor);
        if (hostProfile.isEmpty() || donorProfile.isEmpty() || hostProfile.get().unique() == null || donorProfile.get().unique() == null) return;
        var rules = service.rules();
        Transcendence.Transcendent t;
        try {
            t = Transcendence.shared().resolve(ProfileService.speciesIdOf(host), hostProfile.get().unique().uniqueId(),
                    ProfileService.speciesIdOf(donor), donorProfile.get().unique().uniqueId());
        } catch (CraftException exception) {
            return;
        }
        var wallet = service.store().wallet(player.getUUID());
        var cost = FusionRules.cost();
        String from = t.uniqueIds().stream().map(id -> rules.unique(id).map(u -> u.name()).orElse(id)).reduce((a, b) -> a + " + " + b).orElse("");
        var twist = new StringBuilder();
        for (var op : t.twist().effects()) twist.append(twist.length() == 0 ? "" : " and ").append(op.describe(t.benefitPercent()));
        String rider = rules.affix(t.rider().affix()).base().name() + " +" + Math.round(t.rider().value() * t.benefitPercent() / 100f) + "%";
        ServerPlayNetworking.send(player, new FusionPayloads.Detail(host.getUuid().toString(), donor.getUuid().toString(),
                blockFor(player, service, host, donor), t.name(), from, t.harmony().name().toLowerCase(java.util.Locale.ROOT), t.benefitPercent(),
                t.drawbackPercent(), t.blurb(), t.signature().core(), t.signature().clutch(), t.signature().drawback(), t.twist().name(), twist.toString(),
                rider, t.hostType() == null ? "" : t.hostType(), t.donorType() == null ? "" : t.donorType(),
                new long[] {cost.get(MaterialId.RESONANCE_DUST), cost.get(MaterialId.FACET), cost.get(MaterialId.ASCENSION_CORE), cost.get(MaterialId.UNIQUE_CATALYST)},
                new long[] {wallet.balance(MaterialId.RESONANCE_DUST), wallet.balance(MaterialId.FACET), wallet.balance(MaterialId.ASCENSION_CORE),
                        wallet.balance(MaterialId.UNIQUE_CATALYST)},
                hostProfile.get().revision(), donorProfile.get().revision(), wallet.revision()));
    }

    private static void confirm(ServerPlayer player, FusionPayloads.Confirm request) {
        if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "fusion.confirm", 2, 1)) {
            fail(player, request, "Too many requests. Wait a moment.");
            return;
        }
        var service = AscensionApi.service().orElse(null);
        if (service == null) { fail(player, request, "Progression is unavailable."); return; }
        try { UUID.fromString(request.operationId()); } catch (IllegalArgumentException e) { return; }
        var host = CraftNet.findOwned(player, request.hostId()).orElse(null);
        var donor = CraftNet.findOwned(player, request.donorId()).orElse(null);
        if (host == null || donor == null) { fail(player, request, "Those Pokémon are no longer yours to fuse."); return; }
        try {
            var made = service.fuse(player, host, donor, request.hostRevision(), request.donorRevision(), request.walletRevision());
            LOG.info("fusion committed: {} fused {} into {} -> {} (operation {})", player.getGameProfile().getName(), donor.getUuid(), host.getUuid(),
                    made.id(), request.operationId());
            ServerPlayNetworking.send(player, new FusionPayloads.Result(request.operationId(), true, "", made.name()));
        } catch (StoreException exception) {
            fail(player, request, switch (exception.code()) {
                case STALE_PROFILE, STALE_WALLET -> "A Pokémon or your materials changed. Review it again.";
                case OPERATION_REUSED -> "That confirmation was already used.";
                default -> "The fusion could not be made: " + exception.getMessage();
            });
        } catch (CraftException exception) {
            fail(player, request, exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            fail(player, request, exception.getMessage());
        } catch (RuntimeException exception) {
            LOG.error("Fusion failed for {}", player.getGameProfile().getName(), exception);
            fail(player, request, "Something went wrong; nothing was spent.");
        }
    }

    private static void fail(ServerPlayer player, FusionPayloads.Confirm request, String message) {
        ServerPlayNetworking.send(player, new FusionPayloads.Result(request.operationId(), false, message == null ? "Not allowed." : message, ""));
    }
}
