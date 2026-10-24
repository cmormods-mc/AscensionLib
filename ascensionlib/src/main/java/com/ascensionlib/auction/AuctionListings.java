package com.ascensionlib.auction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The part of SnobbleAuctions' {@code listings.json} the guard needs: which Pokemon the auction currently holds, and for whom. Read with a tolerant parser
 * (not the auction's own classes) so an added field in a later version of the mod changes nothing here.
 */
public final class AuctionListings {
    /** The states in which the auction, not the seller, owns the Pokemon (the mod's own {@code Listing.isHeld}). */
    private static final Set<String> HELD = Set.of("ACTIVE", "PURCHASING", "EXPIRED", "CANCELLED");

    public record Held(String listingId, UUID seller, UUID pokemon, String state) {}

    private AuctionListings() {}

    /**
     * Every Pokemon listing the auction holds. Item listings and entries without a Pokemon UUID are skipped; a malformed entry is skipped too, never guessed
     * at. Throws IllegalArgumentException when the text is not a JSON array at all, so the caller leaves everything alone rather than act on half a file.
     */
    public static List<Held> held(String json) {
        JsonElement root;
        try { root = JsonParser.parseString(json); } catch (RuntimeException exception) { throw new IllegalArgumentException("not JSON", exception); }
        if (root == null || !root.isJsonArray()) throw new IllegalArgumentException("not a listing array");
        JsonArray array = root.getAsJsonArray();
        List<Held> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject listing = element.getAsJsonObject();
            String state = text(listing, "state");
            if (state == null || !HELD.contains(state)) continue;
            if ("ITEM".equals(text(listing, "kind"))) continue;
            UUID seller = uuid(text(listing, "seller"));
            UUID pokemon = uuid(text(listing, "pokemonUuid"));
            String id = text(listing, "id");
            if (seller == null || pokemon == null || id == null) continue;
            out.add(new Held(id, seller, pokemon, state));
        }
        return out;
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    private static UUID uuid(String text) {
        if (text == null) return null;
        try { return UUID.fromString(text); } catch (IllegalArgumentException exception) { return null; }
    }
}
