package nl.loc.backend.config.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@ConfigurationProperties("app.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl) {
    private static final int MIN_KEY_BYTES = 32;

    public JwtProperties {
        if (secret == null || Base64.getDecoder().decode(secret).length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be Base64 of at least 32 bytes; generate one with: openssl rand -base64 32");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }
}
