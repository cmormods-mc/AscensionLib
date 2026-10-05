package com.ascensionlib.scout;

import com.ascensionlib.ProfileService;
import com.cobbleascend.domain.v1.ProfileV1;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.stream.StreamSupport;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** The capture-reveal channel: registered on both sides, sent only by the server, only to the owner, only after the save. */
public final class CaptureRevealNet {
    private CaptureRevealNet() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(CaptureRevealPayload.TYPE, CaptureRevealPayload.CODEC);
    }

    /** What the owner may see of their own Pokemon: rarity, Unique, rolled modifiers, base stats. Never IVs, EVs or moves. */
    static ScoutPayloads.Detail detailOf(Pokemon p, ProfileService service, ProfileV1 value) {
        return new ScoutPayloads.Detail(p.getLevel(),
                StreamSupport.stream(p.getTypes().spliterator(), false).map(type -> type.getName()).toList(),
                ScoutEncounters.baseStats(p.getSpecies()), value.rarity().id(),
                value.unique() == null ? "" : service.rules().unique(value.unique().uniqueId()).map(u -> u.name()).orElse("Unknown Unique"),
                value.ordinarySlots().stream().map(slot -> slotOf(service, slot)).toList());
    }

    /** One owned modifier row: its rolled value, the band that rank rolls in, and what it does (catalog text, nothing secret). */
    static ScoutPayloads.Slot slotOf(ProfileService service, com.cobbleascend.domain.v1.OrdinarySlot slot) {
        var affix = service.rules().affix(slot.affixId());
        var band = affix.band(slot.rank());
        String type = slot.type();
        String condition = affix.base().condition() == null ? "" : affix.base().condition();
        return new ScoutPayloads.Slot(slot.category().id(), affix.base().name() + (type == null ? "" : " (" + type + ")"),
                slot.rank(), slot.rolledValue(), band.min(), band.max(), condition);
    }

    /** Sends the reveal for a committed profile. Quietly does nothing for a client without the channel (older client). */
    public static boolean send(ServerPlayer player, Pokemon pokemon, ProfileService service, ProfileV1 profile) {
        if (!ServerPlayNetworking.canSend(player, CaptureRevealPayload.TYPE)) return false;
        if (!player.getUUID().equals(pokemon.getOwnerUUID())) return false;
        ServerPlayNetworking.send(player, new CaptureRevealPayload(pokemon.getUuid().toString(),
                pokemon.getDisplayName(false).getString(), pokemon.getSpecies().getResourceIdentifier().toString(),
                new ArrayList<>(pokemon.getAspects()), detailOf(pokemon, service, profile)));
        return true;
    }
}
