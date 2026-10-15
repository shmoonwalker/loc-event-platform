package nl.loc.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import nl.loc.backend.user.mail.AccountMailer;
import nl.loc.backend.user.model.Role;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-07T16:00:00Z");
    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final AccountMailer accountMailer = mock(AccountMailer.class);
    private final RegistrationService service = new RegistrationService(
            repository, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC), emailVerificationService, accountMailer);

    @BeforeEach
    void saveReturnsTheSavedAccount() {
        when(repository.saveAndFlush(any(UserAccount.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void savesNewUserWithNormalizedEmailAndEncodedPassword() {
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");

        service.register("  Alice@Example.COM  ", "password", "  Alice  ");

        verify(repository).findByEmail("alice@example.com");
        ArgumentCaptor<UserAccount> account = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).saveAndFlush(account.capture());
        assertThat(account.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(account.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(account.getValue().getDisplayName()).isEqualTo("Alice");
        assertThat(account.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(account.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void sendsVerificationLinkToNewUser() {
        when(emailVerificationService.issueToken(any(UserAccount.class))).thenReturn("raw-token");

        service.register("alice@example.com", "password", "Alice");

        verify(accountMailer).sendVerificationLink(
                argThat(user -> user.getEmail().equals("alice@example.com")), eq("raw-token"));
    }

    @Test
    void hashesPasswordBeforeLookingUpExistingEmail() {
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(verifiedAccount()));

        service.register("  Alice@Example.COM  ", "password", "Alice");

        InOrder order = inOrder(passwordEncoder, repository);
        order.verify(passwordEncoder).encode("password");
        order.verify(repository).findByEmail("alice@example.com");
        order.verifyNoMoreInteractions();
    }

    @Test
    void tellsVerifiedOwnerAboutTheSignUpAttemptWithoutChangingTheAccount() {
        UserAccount existing = verifiedAccount();
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.register("alice@example.com", "new-password", "Mallory");

        verify(accountMailer).sendSignUpAttempt(existing);
        verify(accountMailer, never()).sendVerificationLink(any(), any());
        verify(emailVerificationService, never()).issueToken(any());
        assertThat(existing.getPasswordHash()).isEqualTo("old-hash");
        assertThat(existing.getDisplayName()).isEqualTo("Alice");
    }

    @Test
    void replacesUnverifiedRegistrationAndSendsAFreshLink() {
        UserAccount existing = new UserAccount("alice@example.com", "Mallory", "attacker-hash", NOW);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("alice-password")).thenReturn("alice-hash");
        when(emailVerificationService.issueToken(existing)).thenReturn("fresh-token");

        service.register("alice@example.com", "alice-password", "  Alice  ");

        assertThat(existing.getPasswordHash()).isEqualTo("alice-hash");
        assertThat(existing.getDisplayName()).isEqualTo("Alice");
        InOrder order = inOrder(repository, emailVerificationService, accountMailer);
        order.verify(repository).save(existing);
        order.verify(emailVerificationService).issueToken(existing);
        order.verify(accountMailer).sendVerificationLink(existing, "fresh-token");
    }

    @Test
    void ignoresDuplicateEmailWhenAnotherRegistrationWinsTheRace() {
        when(repository.saveAndFlush(any(UserAccount.class))).thenThrow(
                new DataIntegrityViolationException("duplicate email",
                        new ConstraintViolationException("duplicate email", new SQLException(), "app_user_email_key")));

        assertThatCode(() -> service.register("alice@example.com", "password", "Alice"))
                .doesNotThrowAnyException();
        verify(accountMailer, never()).sendVerificationLink(any(), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "another_constraint")
    void rethrowsOtherOrUnknownConstraintViolations(String constraintName) {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("constraint failure",
                new ConstraintViolationException("constraint failure", new SQLException(), constraintName));
        when(repository.saveAndFlush(any(UserAccount.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.register("alice@example.com", "password", "Alice"))
                .isSameAs(failure);
    }

    @Test
    void rethrowsIntegrityViolationsWithoutAHibernateConstraintCause() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("database failure");
        when(repository.saveAndFlush(any(UserAccount.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.register("alice@example.com", "password", "Alice"))
                .isSameAs(failure);
    }

    @Test
    void resendSendsFreshLinkToUnverifiedAccount() {
        UserAccount unverified = new UserAccount("alice@example.com", "Alice", "hash", NOW);
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(unverified));
        when(emailVerificationService.issueToken(unverified)).thenReturn("raw-token");

        service.resendVerification("  Alice@Example.COM  ");

        verify(accountMailer).sendVerificationLink(unverified, "raw-token");
    }

    @Test
    void resendDoesNothingForVerifiedAccount() {
        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(verifiedAccount()));

        service.resendVerification("alice@example.com");

        verify(emailVerificationService, never()).issueToken(any(UserAccount.class));
        verify(accountMailer, never()).sendVerificationLink(any(UserAccount.class), any());
    }

    @Test
    void resendDoesNothingForUnknownEmail() {
        service.resendVerification("nobody@example.com");

        verify(emailVerificationService, never()).issueToken(any(UserAccount.class));
        verify(accountMailer, never()).sendVerificationLink(any(UserAccount.class), any());
    }

    private static UserAccount verifiedAccount() {
        UserAccount account = new UserAccount("alice@example.com", "Alice", "old-hash", NOW);
        account.markEmailVerified(NOW);
        return account;
    }
}
