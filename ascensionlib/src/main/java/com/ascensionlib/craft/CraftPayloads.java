package com.ascensionlib.craft;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The upgrade / refine / reforge channel. The client asks to see a Pokemon ({@link Open}) and the server answers with a
 * {@link View}: everything is a preview, nothing is spent. A craft happens only when the client sends a {@link Confirm} carrying a
 * fresh operation id and the two revisions it was shown; the server re-checks ownership, revisions and the wallet, commits once, and
 * answers with a {@link Done} and a refreshed view. The client never decides a roll or a cost.
 */
public final class CraftPayloads {
    private CraftPayloads() {}

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("ascensionlib", path); }

    /** Client to server: show me this Pokemon's upgrade screen data. */
    public record Open(String pokemonId) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(id("craft_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeUtf(v.pokemonId(), 36), buf -> new Open(buf.readUtf(36)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client to server: do it. {@code kind} is {@code upgrade}, {@code refine}, {@code reforge}, {@code promote} (no slot), {@code unique} (slotId is the Unique's id; installs or replaces) or {@code assemble} (a Catalyst from Fragments). */
    public record Confirm(String operationId, String pokemonId, String kind, String slotId, long profileRevision, long walletRevision)
            implements CustomPacketPayload {
        public static final Type<Confirm> TYPE = new Type<>(id("craft_confirm"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Confirm> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.operationId(), 36);
            buf.writeUtf(v.pokemonId(), 36);
            buf.writeUtf(v.kind(), 16);
            buf.writeUtf(v.slotId(), 32);
            buf.writeVarLong(v.profileRevision());
            buf.writeVarLong(v.walletRevision() + 1);
        }, buf -> new Confirm(buf.readUtf(36), buf.readUtf(36), buf.readUtf(16), buf.readUtf(32), buf.readVarLong(), buf.readVarLong() - 1));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** One occupied ordinary slot as the upgrade screen shows it. Bands are -1 where there is no next rank. */
    public record SlotView(String slotId, String category, String name, String condition, int rank, int value, int bandMin, int bandMax,
                           int nextMin, int nextMax, String upgradeBlock, String refineBlock, String reforgeBlock, int reforgePool) {
        static void write(RegistryFriendlyByteBuf buf, SlotView v) {
            buf.writeUtf(v.slotId(), 32);
            buf.writeUtf(v.category(), 16);
            buf.writeUtf(v.name(), 128);
            buf.writeUtf(v.condition(), 256);
            buf.writeVarInt(v.rank());
            buf.writeVarInt(v.value());
            buf.writeVarInt(v.bandMin() + 1);
            buf.writeVarInt(v.bandMax() + 1);
            buf.writeVarInt(v.nextMin() + 1);
            buf.writeVarInt(v.nextMax() + 1);
            buf.writeUtf(v.upgradeBlock(), 128);
            buf.writeUtf(v.refineBlock(), 128);
            buf.writeUtf(v.reforgeBlock(), 128);
            buf.writeVarInt(v.reforgePool());
        }
        static SlotView read(RegistryFriendlyByteBuf buf) {
            return new SlotView(buf.readUtf(32), buf.readUtf(16), buf.readUtf(128), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt() - 1,
                    buf.readUtf(128), buf.readUtf(128), buf.readUtf(128), buf.readVarInt());
        }
    }

    /** A material cost: dust, facets, cores. */
    public record Price(int dust, int facets, int cores) {}

    /**
     * The next promotion: {@code toRarity} is empty at the top rarity. {@code block} is the reason it cannot be done now (empty when it
     * can); attunement is the Pokemon's lifetime total against what the next rarity requires.
     */
    public record Promotion(String toRarity, Price price, int attunement, int attunementNeeded, String block) {}

    /** One Unique power a Catalyst can install: its text and whether the Pokemon holds it now. */
    public record UniqueOption(String id, String name, String benefit, String drawback, boolean current) {}

    /**
     * The Unique tab: what the Pokemon holds ({@code currentId}, empty for none), the player's Catalysts and Fragments, the Fragments one
     * Catalyst takes, the powers on offer and the reason an install or replace is blocked (empty when it can be done).
     */
    public record UniqueState(String currentId, long catalysts, long fragments, int fragmentsNeeded, List<UniqueOption> options, String block) {}

    /**
     * Server to client: the screen's data. {@code open} asks the client to open the screen (a command did); otherwise it only
     * refreshes one that is already open. {@code message} is a one-line notice (an error from the last action, or empty).
     */
    public record View(String pokemonId, String name, String speciesId, List<String> aspects, int level, String rarityId, String uniqueName,
                       int pending, int nextMilestone, long profileRevision, long walletRevision, long dust, long facets, long cores,
                       Price refine, Price reforge, Promotion promotion, UniqueState unique, List<SlotView> slots, String message, boolean open) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(id("craft_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.pokemonId(), 36);
            buf.writeUtf(v.name(), 128);
            buf.writeUtf(v.speciesId(), 128);
            buf.writeVarInt(v.aspects().size());
            for (String aspect : v.aspects()) buf.writeUtf(aspect, 64);
            buf.writeVarInt(v.level());
            buf.writeUtf(v.rarityId(), 16);
            buf.writeUtf(v.uniqueName(), 128);
            buf.writeVarInt(v.pending());
            buf.writeVarInt(v.nextMilestone());
            buf.writeVarLong(v.profileRevision());
            buf.writeVarLong(v.walletRevision());
            buf.writeVarLong(v.dust());
            buf.writeVarLong(v.facets());
            buf.writeVarLong(v.cores());
            for (Price price : new Price[] {v.refine(), v.reforge()}) {
                buf.writeVarInt(price.dust());
                buf.writeVarInt(price.facets());
                buf.writeVarInt(price.cores());
            }
            var promotion = v.promotion();
            buf.writeUtf(promotion.toRarity(), 16);
            buf.writeVarInt(promotion.price().dust());
            buf.writeVarInt(promotion.price().facets());
            buf.writeVarInt(promotion.price().cores());
            buf.writeVarInt(promotion.attunement());
            buf.writeVarInt(promotion.attunementNeeded());
            buf.writeUtf(promotion.block(), 128);
            var unique = v.unique();
            buf.writeUtf(unique.currentId(), 64);
            buf.writeVarLong(unique.catalysts());
            buf.writeVarLong(unique.fragments());
            buf.writeVarInt(unique.fragmentsNeeded());
            buf.writeUtf(unique.block(), 128);
            buf.writeVarInt(unique.options().size());
            for (var option : unique.options()) {
                buf.writeUtf(option.id(), 64);
                buf.writeUtf(option.name(), 64);
                buf.writeUtf(option.benefit(), 256);
                buf.writeUtf(option.drawback(), 256);
                buf.writeBoolean(option.current());
            }
            buf.writeVarInt(v.slots().size());
            for (SlotView slot : v.slots()) SlotView.write(buf, slot);
            buf.writeUtf(v.message(), 256);
            buf.writeBoolean(v.open());
        }, buf -> {
            String pokemonId = buf.readUtf(36), name = buf.readUtf(128), species = buf.readUtf(128);
            int aspectCount = Math.min(16, buf.readVarInt());
            List<String> aspects = new ArrayList<>();
            for (int i = 0; i < aspectCount; i++) aspects.add(buf.readUtf(64));
            int level = buf.readVarInt();
            String rarity = buf.readUtf(16), unique = buf.readUtf(128);
            int pending = buf.readVarInt(), next = buf.readVarInt();
            long profileRevision = buf.readVarLong(), walletRevision = buf.readVarLong(), dust = buf.readVarLong(), facets = buf.readVarLong(),
                    cores = buf.readVarLong();
            Price refine = new Price(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            Price reforge = new Price(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            Promotion promotion = new Promotion(buf.readUtf(16), new Price(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(128));
            String currentUnique = buf.readUtf(64);
            long catalysts = buf.readVarLong(), fragments = buf.readVarLong();
            int fragmentsNeeded = buf.readVarInt();
            String uniqueBlock = buf.readUtf(128);
            int optionCount = Math.min(16, buf.readVarInt());
            List<UniqueOption> options = new ArrayList<>();
            for (int i = 0; i < optionCount; i++)
                options.add(new UniqueOption(buf.readUtf(64), buf.readUtf(64), buf.readUtf(256), buf.readUtf(256), buf.readBoolean()));
            UniqueState uniqueState = new UniqueState(currentUnique, catalysts, fragments, fragmentsNeeded, options, uniqueBlock);
            int slotCount = Math.min(8, buf.readVarInt());
            List<SlotView> slots = new ArrayList<>();
            for (int i = 0; i < slotCount; i++) slots.add(SlotView.read(buf));
            return new View(pokemonId, name, species, aspects, level, rarity, unique, pending, next, profileRevision, walletRevision, dust,
                    facets, cores, refine, reforge, promotion, uniqueState, slots, buf.readUtf(256), buf.readBoolean());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: how a confirmed craft ended. On success the slot before and after; on failure only the reason. */
    public record Done(String operationId, boolean ok, String message, String slotId, String oldName, int oldRank, int oldValue,
                       String newName, int newRank, int newValue, boolean replayed) implements CustomPacketPayload {
        public static final Type<Done> TYPE = new Type<>(id("craft_done"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Done> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUtf(v.operationId(), 36);
            buf.writeBoolean(v.ok());
            buf.writeUtf(v.message(), 256);
            buf.writeUtf(v.slotId(), 32);
            buf.writeUtf(v.oldName(), 128);
            buf.writeVarInt(v.oldRank());
            buf.writeVarInt(v.oldValue());
            buf.writeUtf(v.newName(), 128);
            buf.writeVarInt(v.newRank());
            buf.writeVarInt(v.newValue());
            buf.writeBoolean(v.replayed());
        }, buf -> new Done(buf.readUtf(36), buf.readBoolean(), buf.readUtf(256), buf.readUtf(32), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
