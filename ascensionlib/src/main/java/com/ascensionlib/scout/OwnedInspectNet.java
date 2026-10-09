package com.ascensionlib.scout;

import com.ascensionlib.AscensionApi;
import com.cobbleascend.domain.v1.Category;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class OwnedInspectNet {
    private OwnedInspectNet() {}

    /**
     * The player's own Pokemon with this id: in the party or PC, or else the ORIGINAL behind a battle copy in the player's own live
     * battle. A raid or an exhibition fights with copies that carry fresh ids, so the id a battle tile shows is not the party
     * Pokemon's; only the server knows the original. Only an actor that is the requesting player is searched, so an opponent's or a
     * partner's Pokemon can never be resolved this way.
     */
    static Optional<Pokemon> findForInspection(net.minecraft.server.level.ServerPlayer player, UUID id) {
        List<Pokemon> owned = new ArrayList<>();
        Cobblemon.INSTANCE.getStorage().getParty(player).forEach(owned::add);
        Cobblemon.INSTANCE.getStorage().getPC(player).forEach(owned::add);
        var stored = owned.stream().filter(p -> p.getUuid().equals(id) && !p.isBattleClone()
                && player.getUUID().equals(p.getOwnerUUID())).findFirst();
        if (stored.isPresent()) return stored;
        var battle = BattleRegistry.getBattleByParticipatingPlayer(player);
        if (battle == null) return Optional.empty();
        for (var actor : battle.getActors()) {
            if (!player.getUUID().equals(actor.getUuid())) continue;
            for (var member : actor.getPokemonList()) {
                if (!member.getUuid().equals(id)) continue;
                var original = member.getOriginalPokemon();
                if (player.getUUID().equals(original.getOwnerUUID()) && !original.isBattleClone()) return Optional.of(original);
            }
        }
        return Optional.empty();
    }
    public static void register() {
        PayloadTypeRegistry.playC2S().register(OwnedInspectPayload.Request.TYPE, OwnedInspectPayload.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(OwnedInspectPayload.Result.TYPE, OwnedInspectPayload.Result.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OwnedInspectPayload.Request.TYPE, (request, context) -> context.server().execute(() -> {
            UUID id;
            try { id = UUID.fromString(request.pokemonId()); } catch (IllegalArgumentException e) { return; }
            var player = context.player();
            // Each request scans the party and PC and reads the store; the client asks on every open, so throttle it.
            if (!com.ascensionlib.net.RateLimit.allow(player.getUUID(), "inspect.owned", 8, 4)) return;
            var pokemon = findForInspection(player, id);
            if (pokemon.isEmpty() || AscensionApi.service().isEmpty()) {
                ServerPlayNetworking.send(player, new OwnedInspectPayload.Result(request.pokemonId(), "", "", 0, 0, Optional.empty(),
                        OwnedInspectPayload.Extra.NONE));
                return;
            }
            var p = pokemon.get();
            var service = AscensionApi.service().orElseThrow();
            var profile = service.canonical(p);
            var detail = profile.map(value -> CaptureRevealNet.detailOf(p, service, value));
            var extra = new OwnedInspectPayload.Extra(p.getSpecies().getResourceIdentifier().toString(), new ArrayList<>(p.getAspects()),
                    profile.map(v -> v.origin().kind()).orElse(""),
                    profile.map(v -> v.initialRarity().id()).orElse(""),
                    profile.map(v -> v.highestLevelObserved()).orElse(0),
                    profile.map(v -> v.awardedMilestones().size()).orElse(0),
                    profile.map(v -> service.rules().slotCount(v.rarity(), Category.PREFIX)).orElse(0),
                    profile.map(v -> service.rules().slotCount(v.rarity(), Category.SUFFIX)).orElse(0),
                    com.ascensionlib.CraftLocks.locked(p));
            ServerPlayNetworking.send(player, new OwnedInspectPayload.Result(request.pokemonId(), p.getDisplayName(false).getString(),
                    p.getSpecies().getName(), profile.map(v -> v.pendingCredits()).orElse(0),
                    profile.map(v -> v.attunement()).orElse(0), detail, extra));
        }));
    }
}
