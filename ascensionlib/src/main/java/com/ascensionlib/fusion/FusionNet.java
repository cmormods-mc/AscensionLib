package com.ascensionlib.fusion;

import com.ascensionlib.AscensionApi;
import com.ascensionlib.ProfileService;
import com.ascensionlib.craft.CraftNet;
import com.cobbleascend.domain.Rarity;
import com.cobbleascend.domain.v1.CraftException;
import com.cobbleascend.domain.v1.FusionRules;
import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.MaterialWallet;
import com.cobbleascend.domain.v1.ProfileV1;
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

    /** How many PC Pokemon a pick list offers: the first page. */
    private static final int PC_PAGE = 30;

    private FusionNet() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(FusionPayloads.Open.TYPE, FusionPayloads.Open.CODEC);
        PayloadTypeRegistry.playC2S().register(FusionPayloads.Pick.TYPE, FusionPayloads.Pick.CODEC);
        PayloadTypeRegistry.playC2S().register(FusionPayloads.Preview.TYPE, FusionPayloads.Preview.CODEC);
        PayloadTypeRegistry.playC2S().register(FusionPayloads.Confirm.TYPE, FusionPayloads.Confirm.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionPayloads.Candidates.TYPE, FusionPayloads.Candidates.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionPayloads.PickList.TYPE, FusionPayloads.PickList.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionPayloads.Detail.TYPE, FusionPayloads.Detail.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionPayloads.Result.TYPE, FusionPayloads.Result.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FusionPayloads.Open.TYPE, (payload, context) -> context.server().execute(() -> {
            var player = context.player();
            if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "fusion.open", 4, 2)) return;
            try { sendCandidates(player, payload.hostId()); }
            catch (RuntimeException exception) { LOG.error("Fusion open failed for {}", player.getGameProfile().getName(), exception); }
        }));
        ServerPlayNetworking.registerGlobalReceiver(FusionPayloads.Pick.TYPE, (payload, context) -> context.server().execute(() -> {
            var player = context.player();
            if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "fusion.pick", 6, 3)) return;
            try { sendPick(player, payload); }
            catch (RuntimeException exception) { LOG.error("Fusion pick failed for {}", player.getGameProfile().getName(), exception); }
        }));
        ServerPlayNetworking.registerGlobalReceiver(FusionPayloads.Preview.TYPE, (payload, context) -> context.server().execute(() -> {
            var player = context.player();
            if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "fusion.preview", 8, 4)) return;
            try { sendDetail(player, payload.hostId(), payload.donorId()); }
            catch (RuntimeException exception) { LOG.error("Fusion preview failed for {}", player.getGameProfile().getName(), exception); }
        }));
        ServerPlayNetworking.registerGlobalReceiver(FusionPayloads.Confirm.TYPE, (payload, context) -> context.server().execute(() -> confirm(context.player(), payload)));
    }

    /** Text sent to the client is bounded so an unexpectedly long name can never make a packet fail to encode. */
    private static String cap(String text, int max) {
        return text == null ? "" : text.length() <= max ? text : text.substring(0, max);
    }

    /** Why this pairing cannot be done now; empty when it can. The profiles, the wallet and the fused set are read once by the caller. */
    private static String blockFor(Pokemon host, ProfileV1 hostProfile, Pokemon donor, ProfileV1 donorProfile, MaterialWallet wallet,
                                   java.util.Set<UUID> fused) {
        var locked = com.ascensionlib.CraftLocks.reason(host).or(() -> com.ascensionlib.CraftLocks.reason(donor));
        if (locked.isPresent()) return locked.get();
        try {
            FusionRules.check(hostProfile, fused.contains(host.getUuid()), donorProfile, fused.contains(donor.getUuid()), wallet);
            return "";
        } catch (CraftException exception) {
            return exception.getMessage();
        }
    }

    /** Sends the candidate list and asks the client to open the screen (a command did). */
    public static void openFor(ServerPlayer player, Pokemon host) {
        sendCandidates(player, host.getUuid().toString());
    }

    /** Why this Pokemon cannot be a host; empty when it can. */
    private static String hostBlock(Pokemon pokemon, java.util.Optional<ProfileV1> profile, java.util.Set<UUID> fused) {
        var locked = com.ascensionlib.CraftLocks.reason(pokemon);
        if (locked.isPresent()) return locked.get();
        if (profile.isEmpty()) return "That Pokémon has no ascension profile.";
        if (profile.get().rarity() != Rarity.MYTHICAL) return "Only a Mythic Pokémon can be the host.";
        if (profile.get().unique() == null) return "The host needs a Unique.";
        return fused.contains(pokemon.getUuid()) ? "This Pokémon is already a Transcendent." : "";
    }

    /** Why this Pokemon cannot be a donor whatever the host is; empty when it could. */
    private static String donorBlock(Pokemon pokemon, java.util.Optional<ProfileV1> profile, java.util.Set<UUID> fused) {
        var locked = com.ascensionlib.CraftLocks.reason(pokemon);
        if (locked.isPresent()) return locked.get();
        if (profile.isEmpty()) return "No ascension profile.";
        if (profile.get().unique() == null) return "Needs a Unique.";
        if (fused.contains(pokemon.getUuid())) return "Already a Transcendent.";
        return profile.get().rarity().ordinal() < Rarity.EPIC.ordinal() ? "Needs Epic rarity or better." : "";
    }

    private static boolean inParty(ServerPlayer player, Pokemon pokemon) {
        for (var member : Cobblemon.INSTANCE.getStorage().getParty(player)) if (member == pokemon) return true;
        return false;
    }

    private static FusionPayloads.Entry entry(ProfileService service, Pokemon pokemon, java.util.Optional<ProfileV1> profile, boolean inParty,
                                              boolean canHost, String block) {
        return new FusionPayloads.Entry(pokemon.getUuid().toString(), cap(pokemon.getDisplayName(false).getString(), 100),
                pokemon.getSpecies().getResourceIdentifier().toString(), profile.map(p -> p.rarity().id()).orElse(""),
                profile.map(p -> p.unique() == null ? "" : cap(service.rules().unique(p.unique().uniqueId()).map(u -> u.name()).orElse(p.unique().uniqueId()), 60)).orElse(""),
                inParty, canHost, cap(block, 250));
    }

    /** Sends the host and asks the client to open the screen with it in the host chamber (a command or the Fuse button did). */
    static void sendCandidates(ServerPlayer player, String hostId) {
        var service = AscensionApi.service().orElse(null);
        if (service == null || !ServerPlayNetworking.canSend(player, FusionPayloads.Candidates.TYPE)) return;
        var host = CraftNet.findOwned(player, hostId).orElse(null);
        if (host == null) return;
        var profile = service.canonical(host);
        String block = hostBlock(host, profile, service.store().fusedPokemon());
        ServerPlayNetworking.send(player, new FusionPayloads.Candidates(entry(service, host, profile, inParty(player, host), block.isEmpty(), ""), cap(block, 250)));
    }

    /**
     * Answers a {@link FusionPayloads.Pick}: the party, then the first page of the PC ({@link #PC_PAGE} Pokemon). A host list holds only valid
     * hosts (plus the donor chamber's Pokemon, greyed); a donor list holds every Pokemon with the reason it cannot be used, the pairing included.
     */
    static void sendPick(ServerPlayer player, FusionPayloads.Pick request) {
        var service = AscensionApi.service().orElse(null);
        if (service == null || !ServerPlayNetworking.canSend(player, FusionPayloads.PickList.TYPE)) return;
        var fused = service.store().fusedPokemon();
        var wallet = service.store().wallet(player.getUUID());
        var host = request.hostId().isEmpty() ? null : CraftNet.findOwned(player, request.hostId()).orElse(null);
        var hostProfile = host == null ? java.util.Optional.<ProfileV1>empty() : service.canonical(host);
        boolean hostValid = host != null && hostBlock(host, hostProfile, fused).isEmpty();
        var party = new ArrayList<Pokemon>();
        Cobblemon.INSTANCE.getStorage().getParty(player).forEach(party::add);
        var pc = new ArrayList<Pokemon>();
        for (var pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) { if (pc.size() >= PC_PAGE) break; pc.add(pokemon); }
        var entries = new ArrayList<FusionPayloads.Entry>();
        for (boolean fromParty : new boolean[] {true, false}) {
            for (var pokemon : fromParty ? party : pc) {
                if (pokemon.isBattleClone() || !player.getUUID().equals(pokemon.getOwnerUUID())) continue;
                var profile = service.store().profile(pokemon.getUuid());
                boolean canHost = hostBlock(pokemon, profile, fused).isEmpty();
                String id = pokemon.getUuid().toString();
                String block;
                if (request.hostRole()) {
                    if (id.equals(request.donorId())) block = "Already in the donor chamber.";
                    else if (!canHost) continue;
                    else block = "";
                } else if (id.equals(request.hostId())) {
                    block = "Already in the host chamber.";
                } else {
                    block = donorBlock(pokemon, profile, fused);
                    if (block.isEmpty() && hostValid) block = blockFor(host, hostProfile.get(), pokemon, profile.get(), wallet, fused);
                }
                entries.add(entry(service, pokemon, profile, fromParty, canHost, block));
                if (entries.size() >= FusionPayloads.MAX_PICK) break;
            }
        }
        ServerPlayNetworking.send(player, new FusionPayloads.PickList(request.hostRole(), entries));
    }

    static void sendDetail(ServerPlayer player, String hostId, String donorId) {
        var service = AscensionApi.service().orElse(null);
        if (service == null || !ServerPlayNetworking.canSend(player, FusionPayloads.Detail.TYPE)) return;
        var host = CraftNet.findOwned(player, hostId).orElse(null);
        var donor = CraftNet.findOwned(player, donorId).orElse(null);
        if (host == null || donor == null || host == donor) { sendRefusal(player, hostId, donorId, "Those Pokémon are no longer yours to fuse."); return; }
        var hostProfile = service.canonical(host);
        var donorProfile = service.canonical(donor);
        if (hostProfile.isEmpty() || donorProfile.isEmpty() || hostProfile.get().unique() == null || donorProfile.get().unique() == null) {
            sendRefusal(player, hostId, donorId, "Both Pokémon need an ascension profile and a Unique.");
            return;
        }
        var rules = service.rules();
        var wallet = service.store().wallet(player.getUUID());
        var fusedSet = service.store().fusedPokemon();
        Transcendence.Transcendent t;
        try {
            t = Transcendence.shared().resolve(ProfileService.speciesIdOf(host), hostProfile.get().unique().uniqueId(),
                    ProfileService.speciesIdOf(donor), donorProfile.get().unique().uniqueId());
        } catch (CraftException exception) {
            // The client is waiting for an answer: a pairing that cannot be resolved (the same Unique twice) is answered with the reason.
            sendRefusal(player, hostId, donorId, blockFor(host, hostProfile.get(), donor, donorProfile.get(), wallet, fusedSet).isEmpty()
                    ? exception.getMessage() : blockFor(host, hostProfile.get(), donor, donorProfile.get(), wallet, fusedSet));
            return;
        }
        var cost = FusionRules.cost();
        String from = t.uniqueIds().stream().map(id -> rules.unique(id).map(u -> u.name()).orElse(id)).reduce((a, b) -> a + " + " + b).orElse("");
        var twist = new StringBuilder();
        for (var op : t.twist().effects()) twist.append(twist.length() == 0 ? "" : " and ").append(op.describe(t.benefitPercent()));
        String rider = rules.affix(t.rider().affix()).base().name() + " +" + Math.round(t.rider().value() * t.benefitPercent() / 100f) + "%";
        ServerPlayNetworking.send(player, new FusionPayloads.Detail(host.getUuid().toString(), donor.getUuid().toString(),
                cap(blockFor(host, hostProfile.get(), donor, donorProfile.get(), wallet, fusedSet), 250), t.name(), from, t.harmony().name().toLowerCase(java.util.Locale.ROOT), t.benefitPercent(),
                t.drawbackPercent(), t.blurb(), t.signature().core(), t.signature().clutch(), t.signature().drawback(), t.twist().name(), twist.toString(),
                rider, t.hostType() == null ? "" : t.hostType(), t.donorType() == null ? "" : t.donorType(),
                new long[] {cost.get(MaterialId.RESONANCE_DUST), cost.get(MaterialId.FACET), cost.get(MaterialId.ASCENSION_CORE), cost.get(MaterialId.UNIQUE_CATALYST)},
                new long[] {wallet.balance(MaterialId.RESONANCE_DUST), wallet.balance(MaterialId.FACET), wallet.balance(MaterialId.ASCENSION_CORE),
                        wallet.balance(MaterialId.UNIQUE_CATALYST)},
                hostProfile.get().revision(), donorProfile.get().revision(), wallet.revision()));
    }

    /** A Detail with only a reason, so the screen stops waiting and shows why. */
    private static void sendRefusal(ServerPlayer player, String hostId, String donorId, String reason) {
        ServerPlayNetworking.send(player, new FusionPayloads.Detail(cap(hostId, 36), cap(donorId, 36), cap(reason, 250), "", "", "", 1, 1, "", "", "", "", "", "", "",
                "", "", new long[4], new long[4], 0, 0, -1));
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
