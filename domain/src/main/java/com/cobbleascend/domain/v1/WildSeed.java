package com.cobbleascend.domain.v1;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.SplittableRandom;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Deterministic randomness for a wild Pokemon's rating: HMAC-SHA256 over (catalog version, Pokemon ID) keyed with
 * a server-only secret. Without the secret nobody can predict a rating; with it the same Pokemon always rates the
 * same. The catalog version is part of the input, so a content migration is an explicit, visible change.
 */
final class WildSeed {
    static final int MIN_SECRET_BYTES = 16;

    private WildSeed() {}

    static SplittableRandom random(byte[] secret, int catalogVersion, UUID pokemonId) {
        return random(secret, catalogVersion, pokemonId, 1);
    }

    /** A zone tier above 1 is part of the input, so the same Pokemon rates differently at a different tier; tier 1 is exactly the original input. */
    static SplittableRandom random(byte[] secret, int catalogVersion, UUID pokemonId, int tier) {
        if (secret == null || secret.length < MIN_SECRET_BYTES)
            throw new IllegalArgumentException("Wild rating secret must be at least " + MIN_SECRET_BYTES + " bytes");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(("cobbleascend:wild:v1|" + catalogVersion + "|" + pokemonId + (tier > 1 ? "|tier=" + tier : ""))
                    .getBytes(StandardCharsets.UTF_8));
            return new SplittableRandom(ByteBuffer.wrap(digest).getLong());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
