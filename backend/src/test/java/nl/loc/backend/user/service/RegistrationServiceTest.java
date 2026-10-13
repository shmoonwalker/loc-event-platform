package nl.loc.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import nl.loc.backend.user.model.Role;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.hibernate.exception.ConstraintViolationException;
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
    private final RegistrationService service = new RegistrationService(
            repository, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void savesNewUserWithNormalizedEmailAndEncodedPassword() {
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");

        service.register("  Alice@Example.COM  ", "password", "  Alice  ");

        verify(repository).existsByEmail("alice@example.com");
        ArgumentCaptor<UserAccount> account = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).saveAndFlush(account.capture());
        assertThat(account.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(account.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(account.getValue().getDisplayName()).isEqualTo("Alice");
        assertThat(account.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(account.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void hashesPasswordBeforeSkippingExistingEmail() {
        when(repository.existsByEmail("alice@example.com")).thenReturn(true);

        service.register("  Alice@Example.COM  ", "password", "Alice");

        InOrder order = inOrder(passwordEncoder, repository);
        order.verify(passwordEncoder).encode("password");
        order.verify(repository).existsByEmail("alice@example.com");
        order.verifyNoMoreInteractions();
    }

    @Test
    void ignoresDuplicateEmailWhenAnotherRegistrationWinsTheRace() {
        when(repository.saveAndFlush(any(UserAccount.class))).thenThrow(
                new DataIntegrityViolationException("duplicate email",
                        new ConstraintViolationException("duplicate email", new SQLException(), "app_user_email_key")));

        assertThatCode(() -> service.register("alice@example.com", "password", "Alice"))
                .doesNotThrowAnyException();
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
}
