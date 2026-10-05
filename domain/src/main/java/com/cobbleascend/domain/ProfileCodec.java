package com.cobbleascend.domain;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.util.UUID;

/** Strict prototype boundary: preserve invalid/future source data rather than replacing it. */
public final class ProfileCodec {
    private final Gson gson = new Gson();
    private final Rules rules;
    public ProfileCodec(Rules rules) { this.rules = rules; }

    public String encode(Profile profile) {
        rules.validate(profile);
        return gson.toJson(profile);
    }

    public Profile decode(String encoded, UUID expectedPokemon) {
        if (encoded.length() > 16384) throw new IllegalArgumentException("Profile exceeds size limit");
        var object = JsonParser.parseString(encoded).getAsJsonObject();
        for (String key : new String[]{"schemaVersion", "profileId", "pokemonId", "revision", "rarity",
                "initialRarity", "attunement", "origin", "affixes"}) {
            if (!object.has(key) || object.get(key).isJsonNull())
                throw new IllegalArgumentException("Missing profile field: " + key);
        }
        var profile = gson.fromJson(object, Profile.class);
        if (profile == null || !profile.pokemonId().equals(expectedPokemon))
            throw new IllegalArgumentException("Profile belongs to a different Pokemon");
        rules.validate(profile);
        return profile;
    }
}
