package com.ascensionlib.fusion;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The fusion channel. The client asks which Pokemon can be consumed by a host ({@link Open}, answered by {@link Candidates}), asks to see
 * one pairing ({@link Preview}, answered by {@link Detail}) and confirms it ({@link Confirm}, answered by {@link Result}). Everything is
 * a preview until the server accepts a confirmation: the server resolves the Transcendent, applies the rules and the price, and the
 * client only shows the text it is sent.
 */
public final class FusionPayloads {
    private FusionPayloads() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("ascensionlib", path); }

    /** Client to server: which of my Pokemon could this host consume? */
    public record Open(String hostId) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(id("fusion_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeUtf(v.hostId(), 36), buf -> new Open(buf.readUtf(36)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** One possible donor; {@code block} is why it cannot be used now (empty when it can). */
    public record Candidate(String id, String name, String speciesId, String rarityId, String uniqueName, String block) {}

    public record Candidates(String hostId, String hostName, String hostSpeciesId, String hostBlock, List<Candidate> donors) implements CustomPacketPayload {
        public static final Type<Candidates> TYPE = new Type<>(id("fusion_candidates"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Candidates> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.hostId(), 36);
            buf.writeUtf(v.hostName(), 128);
            buf.writeUtf(v.hostSpeciesId(), 128);
            buf.writeUtf(v.hostBlock(), 256);
            buf.writeVarInt(v.donors().size());
            for (var c : v.donors()) {
                buf.writeUtf(c.id(), 36);
                buf.writeUtf(c.name(), 128);
                buf.writeUtf(c.speciesId(), 128);
                buf.writeUtf(c.rarityId(), 16);
                buf.writeUtf(c.uniqueName(), 64);
                buf.writeUtf(c.block(), 256);
            }
        }, buf -> {
            String hostId = buf.readUtf(36), name = buf.readUtf(128), species = buf.readUtf(128), block = buf.readUtf(256);
            int count = Math.min(96, buf.readVarInt());
            List<Candidate> donors = new ArrayList<>();
            for (int i = 0; i < count; i++)
                donors.add(new Candidate(buf.readUtf(36), buf.readUtf(128), buf.readUtf(128), buf.readUtf(16), buf.readUtf(64), buf.readUtf(256)));
            return new Candidates(hostId, name, species, block, donors);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: show me what fusing this donor into this host would make. */
    public record Preview(String hostId, String donorId) implements CustomPacketPayload {
        public static final Type<Preview> TYPE = new Type<>(id("fusion_preview"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Preview> CODEC = StreamCodec.of(
                (buf, v) -> { buf.writeUtf(v.hostId(), 36); buf.writeUtf(v.donorId(), 36); },
                buf -> new Preview(buf.readUtf(36), buf.readUtf(36)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * What a pairing would make, as text. {@code block} is empty when it can be done. The price and the balances travel as plain numbers
     * (dust, facets, cores, catalysts); the three revisions are what a confirmation must carry back.
     */
    public record Detail(String hostId, String donorId, String block, String name, String fromUniques, String harmony, int benefit, int drawback,
                         String blurb, String core, String clutch, String drawbackText, String twistName, String twistText, String riderText,
                         String hostType, String donorType, long[] price, long[] balances, long hostRevision, long donorRevision, long walletRevision)
            implements CustomPacketPayload {
        public static final Type<Detail> TYPE = new Type<>(id("fusion_detail"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Detail> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.hostId(), 36);
            buf.writeUtf(v.donorId(), 36);
            buf.writeUtf(v.block(), 256);
            buf.writeUtf(v.name(), 128);
            buf.writeUtf(v.fromUniques(), 128);
            buf.writeUtf(v.harmony(), 16);
            buf.writeVarInt(v.benefit());
            buf.writeVarInt(v.drawback());
            for (String text : new String[] {v.blurb(), v.core(), v.clutch(), v.drawbackText(), v.twistText(), v.riderText()}) buf.writeUtf(text, 512);
            buf.writeUtf(v.twistName(), 64);
            buf.writeUtf(v.hostType(), 16);
            buf.writeUtf(v.donorType(), 16);
            for (int i = 0; i < 4; i++) buf.writeVarLong(v.price()[i]);
            for (int i = 0; i < 4; i++) buf.writeVarLong(v.balances()[i]);
            buf.writeVarLong(v.hostRevision());
            buf.writeVarLong(v.donorRevision());
            buf.writeVarLong(v.walletRevision() + 1);
        }, buf -> {
            String hostId = buf.readUtf(36), donorId = buf.readUtf(36), block = buf.readUtf(256), name = buf.readUtf(128), from = buf.readUtf(128),
                    harmony = buf.readUtf(16);
            int benefit = buf.readVarInt(), drawback = buf.readVarInt();
            String blurb = buf.readUtf(512), core = buf.readUtf(512), clutch = buf.readUtf(512), drawbackText = buf.readUtf(512),
                    twistText = buf.readUtf(512), rider = buf.readUtf(512), twistName = buf.readUtf(64), hostType = buf.readUtf(16), donorType = buf.readUtf(16);
            long[] price = new long[4], balances = new long[4];
            for (int i = 0; i < 4; i++) price[i] = buf.readVarLong();
            for (int i = 0; i < 4; i++) balances[i] = buf.readVarLong();
            return new Detail(hostId, donorId, block, name, from, harmony, benefit, drawback, blurb, core, clutch, drawbackText, twistName, twistText,
                    rider, hostType, donorType, price, balances, buf.readVarLong(), buf.readVarLong(), buf.readVarLong() - 1);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: do it, with the revisions the player was shown. */
    public record Confirm(String operationId, String hostId, String donorId, long hostRevision, long donorRevision, long walletRevision)
            implements CustomPacketPayload {
        public static final Type<Confirm> TYPE = new Type<>(id("fusion_confirm"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Confirm> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.operationId(), 36);
            buf.writeUtf(v.hostId(), 36);
            buf.writeUtf(v.donorId(), 36);
            buf.writeVarLong(v.hostRevision());
            buf.writeVarLong(v.donorRevision());
            buf.writeVarLong(v.walletRevision() + 1);
        }, buf -> new Confirm(buf.readUtf(36), buf.readUtf(36), buf.readUtf(36), buf.readVarLong(), buf.readVarLong(), buf.readVarLong() - 1));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: how a confirmation ended. */
    public record Result(String operationId, boolean ok, String message, String name) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(id("fusion_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.operationId(), 36);
            buf.writeBoolean(v.ok());
            buf.writeUtf(v.message(), 256);
            buf.writeUtf(v.name(), 128);
        }, buf -> new Result(buf.readUtf(36), buf.readBoolean(), buf.readUtf(256), buf.readUtf(128)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
