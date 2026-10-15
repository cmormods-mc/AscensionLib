package com.ascensionlib.fusion;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class FusionPayloadsTest {
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }

    private static FusionPayloads.Entry entry(int n, boolean party, boolean canHost, String block) {
        return new FusionPayloads.Entry("00000000-0000-0000-0000-00000000000" + (n % 10), "Mon" + n, "cobblemon:mewtwo", "mythical", "Unique " + n, party, canHost, block);
    }

    @Test void candidatesRoundTripWithTheHostEntry() {
        var sent = new FusionPayloads.Candidates(entry(1, true, true, ""), "Only a Mythic Pokémon can be the host.");
        var buf = buffer();
        FusionPayloads.Candidates.CODEC.encode(buf, sent);
        var read = FusionPayloads.Candidates.CODEC.decode(buf);
        assertEquals(sent, read);
        assertEquals("Mon1", read.hostName());
    }

    @Test void pickRoundTripKeepsTheRoleAndBothChambers() {
        var sent = new FusionPayloads.Pick(true, "host-id", "");
        var buf = buffer();
        FusionPayloads.Pick.CODEC.encode(buf, sent);
        assertEquals(sent, FusionPayloads.Pick.CODEC.decode(buf));
    }

    @Test void pickListRoundTripsAndNeverReadsMoreThanOnePage() {
        var entries = new ArrayList<FusionPayloads.Entry>();
        for (int i = 0; i < FusionPayloads.MAX_PICK; i++) entries.add(entry(i, i < 6, i % 2 == 0, i % 3 == 0 ? "No ascension profile." : ""));
        var buf = buffer();
        FusionPayloads.PickList.CODEC.encode(buf, new FusionPayloads.PickList(false, entries));
        var read = FusionPayloads.PickList.CODEC.decode(buf);
        assertFalse(read.hostRole());
        assertEquals(entries, read.entries());

        var oversized = new ArrayList<>(entries);
        for (int i = 0; i < 40; i++) oversized.add(entry(i, false, false, ""));
        var big = buffer();
        FusionPayloads.PickList.CODEC.encode(big, new FusionPayloads.PickList(true, oversized));
        assertEquals(FusionPayloads.MAX_PICK, FusionPayloads.PickList.CODEC.decode(big).entries().size(), "A hostile or buggy server list is cut to one page");
    }
}
