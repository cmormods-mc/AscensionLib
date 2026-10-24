package com.ascensionlib.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuctionListingsTest {
    private static final String SELLER = "11111111-1111-1111-1111-111111111111";
    private static final String MON = "22222222-2222-2222-2222-222222222222";

    private static String listing(String state, String extra) {
        return "{\"id\":\"abc\",\"seller\":\"" + SELLER + "\",\"state\":\"" + state + "\",\"pokemonUuid\":\"" + MON + "\"" + extra + "}";
    }

    @Test
    void heldStatesAreReturnedAndSettledOnesAreNot() {
        for (String state : new String[] {"ACTIVE", "PURCHASING", "EXPIRED", "CANCELLED"}) {
            var held = AuctionListings.held("[" + listing(state, "") + "]");
            assertEquals(1, held.size(), state);
            assertEquals(UUID.fromString(MON), held.get(0).pokemon());
            assertEquals(UUID.fromString(SELLER), held.get(0).seller());
        }
        for (String state : new String[] {"PENDING_ESCROW", "SOLD", "RETURNED"}) {
            assertTrue(AuctionListings.held("[" + listing(state, "") + "]").isEmpty(), state);
        }
    }

    @Test
    void itemListingsAndMalformedEntriesAreSkipped() {
        assertTrue(AuctionListings.held("[" + listing("ACTIVE", ",\"kind\":\"ITEM\"") + "]").isEmpty());
        assertEquals(1, AuctionListings.held("[" + listing("ACTIVE", ",\"kind\":\"POKEMON\"") + "]").size());
        assertTrue(AuctionListings.held("[{\"id\":\"x\",\"state\":\"ACTIVE\",\"seller\":\"not-a-uuid\",\"pokemonUuid\":\"" + MON + "\"}]").isEmpty());
        assertTrue(AuctionListings.held("[{\"id\":\"x\",\"state\":\"ACTIVE\",\"seller\":\"" + SELLER + "\"}]").isEmpty());
        assertTrue(AuctionListings.held("[]").isEmpty());
    }

    @Test
    void textThatIsNotAListingArrayIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AuctionListings.held("{\"a\":1}"));
        assertThrows(IllegalArgumentException.class, () -> AuctionListings.held("{{{ half a file"));
    }
}
