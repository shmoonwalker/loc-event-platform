package nl.loc.backend.user.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import nl.loc.backend.user.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class AccountMailerTest {
    private static final String FROM = "Loc <no-reply@loc.test>";
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    // trailing slash on purpose: links must not come out as //verify-email
    private final AccountMailer mailer = new AccountMailer(
            mailSender, new AccountMailProperties(FROM, "http://localhost:4200/"));
    private final UserAccount user = new UserAccount(
            "alice@example.com", "Alice", "hash", Instant.parse("2030-06-07T16:00:00Z"));

    @Test
    void verificationMailContainsLinkToFrontendPage() {
        mailer.sendVerificationLink(user, "abc_123-XYZ");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getText()).contains("http://localhost:4200/verify-email?token=abc_123-XYZ");
    }

    @Test
    void signUpAttemptMailLinksToPasswordResetAndHasNoVerifyLink() {
        mailer.sendSignUpAttempt(user);

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getText())
                .contains("http://localhost:4200/forgot-password")
                .doesNotContain("verify-email");
    }

    @Test
    void mailFailureIsLoggedNotThrown() {
        doThrow(new MailSendException("down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailer.sendVerificationLink(user, "token")).doesNotThrowAnyException();
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        return message.getValue();
    }
}
