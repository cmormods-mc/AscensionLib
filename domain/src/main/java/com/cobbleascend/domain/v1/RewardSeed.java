package com.cobbleascend.domain.v1;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable seeds for reward rolls, so a retried settlement recomputes exactly what the first attempt did. */
final class RewardSeed {
    private RewardSeed() {}

    static long of(String purpose, Object... parts) {
        var text = new StringBuilder("cobbleascend:").append(purpose).append(":v1");
        for (var part : parts) text.append('|').append(part);
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
