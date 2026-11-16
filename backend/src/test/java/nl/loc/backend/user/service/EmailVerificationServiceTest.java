package nl.loc.backend.user.service;

import static nl.loc.backend.user.service.EmailVerificationService.TOKEN_TTL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import nl.loc.backend.user.model.EmailVerificationToken;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class EmailVerificationServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-07T16:00:00Z");
    private static final String RAW_TOKEN = "raw-token";
    private final EmailVerificationTokenRepository repository = mock(EmailVerificationTokenRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final EmailVerificationService service = new EmailVerificationService(
            repository, Clock.fixed(NOW, ZoneOffset.UTC), users, events);
    private final UserAccount user = new UserAccount("alice@example.com", "Alice", "hash", NOW);

    @Test
    void issueTokenStoresOnlyTheHashAndExpiresAfterTtl() {
        String rawToken = service.issueToken(user);

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SecureTokens.hash(rawToken));
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(TOKEN_TTL));
        assertThat(saved.getValue().getUsedAt()).isNull();
    }

    @Test
    void issueTokenInvalidatesOldTokensBeforeSavingTheNewOne() {
        service.issueToken(user);

        InOrder order = inOrder(repository);
        order.verify(repository).invalidateUnused(user, NOW);
        order.verify(repository).save(any(EmailVerificationToken.class));
    }

    @Test
    void verifyMarksTokenUsedAndUserVerified() {
        EmailVerificationToken token = storedToken(NOW);

        service.verify(RAW_TOKEN);

        assertThat(token.getUsedAt()).isEqualTo(NOW);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void verifyChangeReplacesEmailAndSchedulesNoticeToPreviousAddress() {
        EmailVerificationToken token = storedToken(NOW, "new@example.com");

        service.verify(RAW_TOKEN);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        assertThat(token.getNewEmail()).isNull();
        verify(events).publishEvent(new EmailChangeService.EmailChanged(user.getId(), "alice@example.com"));
    }

    @Test
    void verifyRejectsUnknownToken() {
        when(repository.findByTokenHash(aryEq(SecureTokens.hash(RAW_TOKEN)))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(RAW_TOKEN)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyRejectsExpiredToken() {
        EmailVerificationToken token = storedToken(NOW.minus(TOKEN_TTL).minus(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> service.verify(RAW_TOKEN)).isInstanceOf(InvalidTokenException.class);
        assertThat(token.getUsedAt()).isNull();
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyRejectsTokenExactlyAtExpiry() {
        storedToken(NOW.minus(TOKEN_TTL));

        assertThatThrownBy(() -> service.verify(RAW_TOKEN)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verifyRejectsUsedToken() {
        EmailVerificationToken token = storedToken(NOW);
        token.markUsed(NOW);

        assertThatThrownBy(() -> service.verify(RAW_TOKEN)).isInstanceOf(InvalidTokenException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }

    private EmailVerificationToken storedToken(Instant createdAt) {
        return storedToken(createdAt, null);
    }

    private EmailVerificationToken storedToken(Instant createdAt, String newEmail) {
        UUID owner = UUID.randomUUID();
        when(repository.findOwnerByTokenHash(aryEq(SecureTokens.hash(RAW_TOKEN)))).thenReturn(Optional.of(owner));
        when(users.findLockedById(owner)).thenReturn(Optional.of(user));
        EmailVerificationToken token = new EmailVerificationToken(
                user, SecureTokens.hash(RAW_TOKEN), createdAt, TOKEN_TTL, newEmail);
        when(repository.findByTokenHash(aryEq(SecureTokens.hash(RAW_TOKEN)))).thenReturn(Optional.of(token));
        return token;
    }
}
