package com.ascensionlib.scout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Read-only own-Pokemon inspection. The server verifies party/PC membership before replying. */
public final class OwnedInspectPayload {
    private OwnedInspectPayload() {}
    public record Request(String pokemonId) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ascensionlib", "inspect_owned"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(36), Request::pokemonId, Request::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * What else the owner may see about the profile: where the Pokemon came from, how far it has come, how many modifier slots
     * its rarity allows, and the identifiers the client needs to draw it. Catalog and profile facts only; never IVs, EVs or moves.
     */
    public record Extra(String speciesId, List<String> aspects, String origin, String initialRarity, int highestLevel,
                        int milestones, int prefixCap, int suffixCap, boolean craftLocked) {
        public static final Extra NONE = new Extra("", List.of(), "", "", 0, 0, 0, 0, false);
        static final StreamCodec<RegistryFriendlyByteBuf, Extra> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.speciesId(), 128);
            buf.writeVarInt(v.aspects().size());
            for (String aspect : v.aspects()) buf.writeUtf(aspect, 64);
            buf.writeUtf(v.origin(), 64);
            buf.writeUtf(v.initialRarity(), 16);
            buf.writeVarInt(v.highestLevel());
            buf.writeVarInt(v.milestones());
            buf.writeVarInt(v.prefixCap());
            buf.writeVarInt(v.suffixCap());
            buf.writeBoolean(v.craftLocked());
        }, buf -> {
            String species = buf.readUtf(128);
            int count = Math.min(16, buf.readVarInt());
            List<String> aspects = new ArrayList<>();
            for (int i = 0; i < count; i++) aspects.add(buf.readUtf(64));
            return new Extra(species, aspects, buf.readUtf(64), buf.readUtf(16), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        });
    }

    public record Result(String pokemonId, String name, String species, int pending, int attunement,
                         Optional<ScoutPayloads.Detail> detail, Extra extra) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ascensionlib", "inspect_owned_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.pokemonId(), 36);
            buf.writeUtf(v.name(), 128);
            buf.writeUtf(v.species(), 128);
            buf.writeVarInt(v.pending());
            buf.writeVarInt(v.attunement());
            ByteBufCodecs.optional(ScoutPayloads.Detail.CODEC).encode(buf, v.detail());
            Extra.CODEC.encode(buf, v.extra());
        }, buf -> new Result(buf.readUtf(36), buf.readUtf(128), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(),
                ByteBufCodecs.optional(ScoutPayloads.Detail.CODEC).decode(buf), Extra.CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
