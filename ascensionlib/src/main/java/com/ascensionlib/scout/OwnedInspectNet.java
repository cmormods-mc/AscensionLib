package com.ascensionlib.scout;

import com.ascensionlib.AscensionApi;
import com.cobbleascend.domain.v1.Category;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class OwnedInspectNet {
    private OwnedInspectNet() {}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(OwnedInspectPayload.Request.TYPE, OwnedInspectPayload.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(OwnedInspectPayload.Result.TYPE, OwnedInspectPayload.Result.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OwnedInspectPayload.Request.TYPE, (request, context) -> context.server().execute(() -> {
            UUID id;
            try { id = UUID.fromString(request.pokemonId()); } catch (IllegalArgumentException e) { return; }
            var player = context.player();
            List<Pokemon> owned = new ArrayList<>();
            Cobblemon.INSTANCE.getStorage().getParty(player).forEach(owned::add);
            Cobblemon.INSTANCE.getStorage().getPC(player).forEach(owned::add);
            var pokemon = owned.stream().filter(p -> p.getUuid().equals(id) && !p.isBattleClone()
                    && player.getUUID().equals(p.getOwnerUUID())).findFirst();
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
