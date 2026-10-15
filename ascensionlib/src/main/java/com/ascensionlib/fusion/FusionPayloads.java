package com.ascensionlib.fusion;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The fusion messages. The screen is a web page shown by WebGUI, so everything but {@link Open} travels as JSON through {@link FusionWeb}
 * (the records below are what the server serializes; the page only displays the text it is sent). The page asks to see one pairing
 * ({@code ascension_fusion_preview}, answered by {@link Detail}) and confirms it ({@code ascension_fusion_confirm}, answered by
 * {@link Result}). Everything is a preview until the server accepts a confirmation: the server resolves the Transcendent, applies the
 * rules and the price.
 *
 * <p>{@link Open} is the one native packet left: the craft screen's Fuse button sends it, and the server answers by opening the web page.
 * It goes away when the craft screen is a page too.
 */
public final class FusionPayloads {
    private FusionPayloads() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("ascensionlib", path); }

    /** Client to server: open the fusion page with this Pokemon as the host. */
    public record Open(String hostId) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(id("fusion_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeUtf(v.hostId(), 36), buf -> new Open(buf.readUtf(36)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** One Pokemon the player owns, as the picker and the chambers show it; {@code block} is why it cannot take the asked-for role now (empty when it can). */
    public record Entry(String id, String name, String speciesId, String rarityId, String uniqueName, boolean inParty, boolean canHost, String block) {}

    /** The most entries a pick list carries: a full party plus the first page of the PC. */
    public static final int MAX_PICK = 36;

    /** Server to page: the host for the host chamber; {@code hostBlock} is why it cannot be a host (empty when it can). */
    public record Candidates(Entry host, String hostBlock) {}

    /** Server to page: the Pokemon a chamber could hold: party first, then the first page of the PC. */
    public record PickList(boolean hostRole, List<Entry> entries) {}

    /**
     * What a pairing would make, as text. {@code block} is empty when it can be done. The price and the balances are plain numbers
     * (dust, facets, cores, catalysts); the three revisions are what a confirmation must carry back.
     */
    public record Detail(String hostId, String donorId, String block, String name, String fromUniques, String harmony, int benefit, int drawback,
                         String blurb, String core, String clutch, String drawbackText, String twistName, String twistText, String riderText,
                         String hostType, String donorType, long[] price, long[] balances, long hostRevision, long donorRevision, long walletRevision) {}

    /** Server to page: how a confirmation ended. */
    public record Result(String operationId, boolean ok, String message, String name) {}
}
