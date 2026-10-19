package nl.loc.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.config.jwt.JwtProperties;
import nl.loc.backend.user.model.RefreshToken;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-07T16:00:00Z");
    private static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final String RAW_TOKEN = "raw-refresh-token";
    private static final UUID FAMILY_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SUCCESSOR_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
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

    @Test
    void rotateReplacesActiveTokenAndReturnsNewRawTokenInSameFamily() {
        RefreshToken existing = storedActiveToken();

        when(repository.saveAndFlush(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", SUCCESSOR_ID);
            return saved;
        });

        RefreshRotationResult result = service.rotate(RAW_TOKEN);

        assertThat(result.user()).isSameAs(user);
        assertThat(result.rawRefreshToken()).isNotBlank();

        ArgumentCaptor<RefreshToken> flushed = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).saveAndFlush(flushed.capture());
        assertThat(flushed.getValue().getFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(flushed.getValue().getTokenHash()).isEqualTo(SecureTokens.hash(result.rawRefreshToken()));
        assertThat(flushed.getValue().getUser()).isSameAs(user);

        ArgumentCaptor<RefreshToken> updated = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(updated.capture());
        assertThat(updated.getValue()).isSameAs(existing);
        assertThat(existing.getReplacedBy()).isEqualTo(SUCCESSOR_ID);
    }

    @Test
    void rotateKeepsTheFamilyExpiryInsteadOfExtendingIt() {
        Instant loggedInAt = NOW.minus(Duration.ofDays(3));
        RefreshToken existing = new RefreshToken(user, SecureTokens.hash(RAW_TOKEN), FAMILY_ID, loggedInAt, REFRESH_TTL);
        when(repository.findByTokenHash(aryEq(SecureTokens.hash(RAW_TOKEN)))).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshRotationResult result = service.rotate(RAW_TOKEN);

        ArgumentCaptor<RefreshToken> flushed = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).saveAndFlush(flushed.capture());
        assertThat(flushed.getValue().getExpiresAt()).isEqualTo(loggedInAt.plus(REFRESH_TTL));
        assertThat(result.remainingTtl()).isEqualTo(Duration.ofDays(11));
    }

    @Test
    void rotateRejectsUnknownExpiredAndRevokedTokens() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(InvalidRefreshTokenException.class);

        RefreshToken expired = new RefreshToken(
                user, SecureTokens.hash(RAW_TOKEN), FAMILY_ID, NOW.minus(REFRESH_TTL), REFRESH_TTL);
        when(repository.findByTokenHash(aryEq(SecureTokens.hash(RAW_TOKEN)))).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.rotate(RAW_TOKEN)).isInstanceOf(InvalidRefreshTokenException.class);

        RefreshToken revoked = storedActiveToken();
        revoked.revoke(NOW);
        assertThatThrownBy(() -> service.rotate(RAW_TOKEN)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).revokeActiveByFamilyId(any(), any());
    }

    @Test
    void rotateRevokesFamilyWhenPresentedTokenWasAlreadyReplaced() {
        RefreshToken existing = storedActiveToken();
        existing.markReplacedBy(SUCCESSOR_ID);
        storedSuccessorCreatedAt(NOW.minus(RefreshTokenService.REUSE_GRACE));

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository).revokeActiveByFamilyId(FAMILY_ID, NOW);
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).save(any());
    }

    @Test
    void rotateWithinGraceOfTheRotationIsAParallelRefreshNotReuse() {
        RefreshToken existing = storedActiveToken();
        existing.markReplacedBy(SUCCESSOR_ID);
        storedSuccessorCreatedAt(NOW.minusSeconds(2));

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN)).isInstanceOf(RefreshTokenAlreadyRotatedException.class);

        verify(repository, never()).revokeActiveByFamilyId(any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rotateOfARevokedReplacedTokenIsRejectedWithoutGrace() {
        RefreshToken existing = storedActiveToken();
        existing.markReplacedBy(SUCCESSOR_ID);
        existing.revoke(NOW);
        storedSuccessorCreatedAt(NOW.minusSeconds(2));

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void deleteExpiredRemovesRowsThatExpiredBeforeNow() {
        service.deleteExpired();

        verify(repository).deleteExpiredBefore(NOW);
    }

    @Test
    void logoutRevokesFamilyWhenTokenIsKnown() {
        storedActiveToken();

        service.logout(RAW_TOKEN);

        verify(repository).revokeActiveByFamilyId(FAMILY_ID, NOW);
    }

    @Test
    void logoutDoesNotRevokeWhenTokenIsUnknown() {
        when(repository.findByTokenHash(aryEq(SecureTokens.hash(RAW_TOKEN)))).thenReturn(Optional.empty());

        service.logout(RAW_TOKEN);

        verify(repository, never()).revokeActiveByFamilyId(any(), any());
    }

    @Test
    void logoutDoesNotTouchRepositoryWhenTokenIsBlank() {
        service.logout(" ");

        verify(repository, never()).findByTokenHash(any());
        verify(repository, never()).revokeActiveByFamilyId(any(), any());
    }

    private void storedSuccessorCreatedAt(Instant createdAt) {
        RefreshToken successor = new RefreshToken(user, SecureTokens.hash("successor"), FAMILY_ID, createdAt, REFRESH_TTL);
        when(repository.findById(SUCCESSOR_ID)).thenReturn(Optional.of(successor));
    }

    private RefreshToken storedActiveToken() {
        RefreshToken token = new RefreshToken(
                user, SecureTokens.hash(RAW_TOKEN), FAMILY_ID, NOW, REFRESH_TTL);
        when(repository.findByTokenHash(aryEq(SecureTokens.hash(RAW_TOKEN)))).thenReturn(Optional.of(token));
        return token;
    }
}
