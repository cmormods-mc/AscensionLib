package com.ascensionlib.scout;

import com.ascensionlib.AscensionLib;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The scouting channel. The server sends one {@link State} whenever something changes for a player; the client only
 * ever shows the latest. An unscouted entry carries a name and nothing else, so the packet itself cannot leak a rarity,
 * a modifier, a Unique or even a level (the enemy's name is what the battle already shows).
 */
public final class ScoutPayloads {
    private ScoutPayloads() {}

    /** One modifier row of a scouted enemy; {@code category} is {@code prefix} or {@code suffix}. */
    public record Slot(String category, String name, int rank, int value, int min, int max, String condition) {
        /** A row with no range or effect text (a scouted enemy's: the reveal shows what it always did). */
        public Slot(String category, String name, int rank, int value) { this(category, name, rank, value, -1, -1, ""); }
        static final StreamCodec<RegistryFriendlyByteBuf, Slot> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.category(), 16);
            buf.writeUtf(v.name(), 128);
            buf.writeVarInt(v.rank());
            buf.writeVarInt(v.value());
            buf.writeVarInt(v.min() + 1);
            buf.writeVarInt(v.max() + 1);
            buf.writeUtf(v.condition(), 256);
        }, buf -> new Slot(buf.readUtf(16), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readUtf(256)));
    }

    /**
     * What a reveal shows. {@code baseStats} is HP, Attack, Defence, Sp. Atk, Sp. Def, Speed (empty when unknown);
     * {@code uniqueName} is empty when the enemy has none. Never IVs, EVs or moves.
     */
    public record Detail(int level, List<String> types, List<Integer> baseStats, String rarityId, String uniqueName,
                         List<Slot> slots) {
        static final StreamCodec<RegistryFriendlyByteBuf, Detail> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Detail::level,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Detail::types,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Detail::baseStats,
                ByteBufCodecs.STRING_UTF8, Detail::rarityId,
                ByteBufCodecs.STRING_UTF8, Detail::uniqueName,
                Slot.CODEC.apply(ByteBufCodecs.list()), Detail::slots,
                Detail::new);
    }

    public record Entry(String subjectId, String name, Optional<Detail> detail) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::subjectId,
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.optional(Detail.CODEC), Entry::detail,
                Entry::new);
    }

    /** Server to client: the player's current encounter (empty id and entries when there is none) and Scouter count. */
    public record State(String encounterId, int scouters, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(id("scout_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, State::encounterId,
                ByteBufCodecs.VAR_INT, State::scouters,
                Entry.CODEC.apply(ByteBufCodecs.list()), State::entries,
                State::new);

        public static State none(int scouters) { return new State("", scouters, List.of()); }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: send me my current state (opening the screen). */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("scout_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: spend a Scouter to reveal this enemy of this encounter. */
    public record Use(String encounterId, String subjectId) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(id("scout_use"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Use::encounterId,
                ByteBufCodecs.STRING_UTF8, Use::subjectId,
                Use::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AscensionLib.MOD_ID, path);
    }
}
