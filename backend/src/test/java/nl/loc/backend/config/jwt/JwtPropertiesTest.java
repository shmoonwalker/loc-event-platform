package nl.loc.backend.config.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtPropertiesTest {
    private static final String VALID_SECRET = "n7bnao9xAfkJrxJHGeJPlgCURpkQNnJDWzcjdEkot3k=";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(JwtPropertiesConfig.class);

    @Test
    void acceptsBase64SecretOf32Bytes() {
        assertThat(new JwtProperties(VALID_SECRET, TTL).secretKey().getEncoded()).hasSize(32);
    }

    @Test
    void rejectsMissingShortAndNonBase64Secrets() {
        assertThatThrownBy(() -> new JwtProperties(null, TTL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("", TTL)).isInstanceOf(IllegalArgumentException.class);
        // 31 bytes, one short of 256 bits
        assertThatThrownBy(() -> new JwtProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", TTL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("not base64!", TTL)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applicationDoesNotStartWithoutValidSecret() {
        context.run(started -> assertThat(started).hasFailed());
        context.withPropertyValues("app.jwt.secret=too-short").run(started -> assertThat(started).hasFailed());
        context.withPropertyValues("app.jwt.secret=" + VALID_SECRET).run(started -> assertThat(started).hasNotFailed());
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtPropertiesConfig {
    }
}
