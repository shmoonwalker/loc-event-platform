package nl.loc.backend.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SecureTokens() {
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] hash(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
}
