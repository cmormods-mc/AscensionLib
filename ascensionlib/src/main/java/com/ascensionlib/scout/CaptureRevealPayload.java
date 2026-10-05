package com.ascensionlib.scout;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, recipient only: the outcome of a capture, sent after the profile is committed. Read-only; there is no
 * reply, so closing or skipping the reveal cannot reroll, grant, remove or duplicate anything. {@code speciesId} and
 * {@code aspects} let the client draw Cobblemon's own model without the Pokemon being in its synced storage (it may be in the PC).
 */
public record CaptureRevealPayload(String pokemonId, String name, String speciesId, List<String> aspects,
                                   ScoutPayloads.Detail detail) implements CustomPacketPayload {
    public static final Type<CaptureRevealPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ascensionlib", "capture_reveal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureRevealPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), CaptureRevealPayload::pokemonId,
            ByteBufCodecs.stringUtf8(128), CaptureRevealPayload::name,
            ByteBufCodecs.stringUtf8(128), CaptureRevealPayload::speciesId,
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(16)), CaptureRevealPayload::aspects,
            ScoutPayloads.Detail.CODEC, CaptureRevealPayload::detail,
            CaptureRevealPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
