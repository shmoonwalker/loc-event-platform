package nl.loc.backend.config.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtConfigTest {
    private static final JwtProperties PROPERTIES = new JwtProperties(
            "n7bnao9xAfkJrxJHGeJPlgCURpkQNnJDWzcjdEkot3k=", Duration.ofMinutes(15), Duration.ofDays(14));
    private static final JwtProperties OTHER_PROPERTIES = new JwtProperties(
            "8m8qJx6Ow3tKkK3PZ0a4cQ2u7L1vYb9sN5eR0dT6fHg=", Duration.ofMinutes(15), Duration.ofDays(14));

    private final JwtConfig config = new JwtConfig();

    @Test
    void decoderAcceptsTokenSignedByEncoder() {
        String token = encode(config.jwtEncoder(PROPERTIES));

        assertThat(config.jwtDecoder(PROPERTIES).decode(token).getSubject()).isEqualTo("test");
    }

    @Test
    void decoderRejectsTokenSignedWithAnotherSecret() {
        String token = encode(config.jwtEncoder(OTHER_PROPERTIES));
        JwtDecoder decoder = config.jwtDecoder(PROPERTIES);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private static String encode(JwtEncoder encoder) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("test")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .build();
        // without an explicit HS256 header the encoder looks for an RS256 key and fails
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
