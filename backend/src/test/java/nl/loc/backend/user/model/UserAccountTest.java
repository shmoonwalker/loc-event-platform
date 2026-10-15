package nl.loc.backend.user.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAccountTest {
    private static final Instant NOW = Instant.parse("2030-06-07T16:00:00Z");

    @Test
    void refusesToReplaceRegistrationOfVerifiedAccount() {
        UserAccount account = new UserAccount("alice@example.com", "Alice", "alice-hash", NOW);
        account.markEmailVerified(NOW);

        assertThatThrownBy(() -> account.replaceRegistration("attacker-hash", "Mallory"))
                .isInstanceOf(IllegalStateException.class);
    }
}
