package nl.loc.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import nl.loc.backend.config.jwt.JwtProperties;
import nl.loc.backend.user.model.RefreshToken;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-07T16:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "n7bnao9xAfkJrxJHGeJPlgCURpkQNnJDWzcjdEkot3k=", Duration.ofMinutes(15), REFRESH_TTL);

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(
            repository, JWT_PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UserAccount user = new UserAccount("alice@example.com", "Alice", "hash", NOW);

    @Test
    void issueTokenStoresOnlyTheHashWithFamilyIdAndExpiresAfterTtl() {
        String rawToken = service.issueToken(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(rawToken).isNotBlank();
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SecureTokens.hash(rawToken));
        assertThat(saved.getValue().getFamilyId()).isNotNull();
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
        assertThat(saved.getValue().getRevokedAt()).isNull();
    }
}
