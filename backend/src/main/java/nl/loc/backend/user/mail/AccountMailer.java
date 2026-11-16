package nl.loc.backend.user.mail;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.loc.backend.config.ratelimit.RateLimitExceededException;
import nl.loc.backend.config.ratelimit.RateLimitPolicy;
import nl.loc.backend.config.ratelimit.RateLimiter;
import nl.loc.backend.user.model.UserAccount;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class AccountMailer {

    private final JavaMailSender mailSender;
    private final AccountMailProperties properties;
    private final RateLimiter rateLimiter;

    public AccountMailer(JavaMailSender mailSender, AccountMailProperties properties, RateLimiter rateLimiter) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Async
    public void sendVerificationLink(UserAccount user, String rawToken) {
        String text = """
                Welcome to Loc!

                Verify your email address using this link:
                %s

                This link is valid for 24 hours.

                If you didn't create this account, you can ignore this email.
                """.formatted(verificationLink(rawToken));

        sendLimited(user.getId(), user.getEmail(), "verification", "Verify your email address", text);
    }

    private String verificationLink(String rawToken) {
        return UriComponentsBuilder
                .fromUriString(properties.frontendUrl())
                .path("/verify-email")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }

    @Async
    public void sendEmailChangeLink(UUID userId, String newEmail, String rawToken) {
        String text = """
                Confirm this address for your Loc account using the link below.
                The link expires in 24 hours.

                %s

                If you didn't request this change, you can ignore this email.
                """.formatted(verificationLink(rawToken));
        sendLimited(userId, newEmail, "email change", "Confirm your new email address", text);
    }

    @Async
    public void sendPasswordResetLink(UUID userId, String address, String rawToken) {
        String link = UriComponentsBuilder
                .fromUriString(properties.frontendUrl())
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        String text = """
                Someone asked to reset the password of your Loc account.

                Choose a new password using this link:
                %s

                This link is valid for 30 minutes and works once.

                If you didn't ask for this, you can ignore this email.
                Your password hasn't been changed.
                """.formatted(link);

        sendLimited(userId, address, "password reset", "Reset your Loc password", text);
    }

    @Async
    public void sendEmailChanged(UUID userId, String oldEmail) {
        // This security notice must still go out when the account has exhausted its link quota.
        send(userId, oldEmail, "email changed", "Your Loc email address has changed",
                "The email address for your Loc account has changed. You can now sign in with your new address.");
    }

    @Async
    public void sendSignUpAttempt(UserAccount user) {
        String link = UriComponentsBuilder
                .fromUriString(properties.frontendUrl())
                .path("/forgot-password")
                .build()
                .encode()
                .toUriString();

        String text = """
                Someone tried to sign up for Loc using your email address.

                You already have an account. If this was you and you forgot
                your password, you can reset it here:
                %s

                If this wasn't you, you can ignore this email.
                Your account hasn't been changed.
                """.formatted(link);

        sendLimited(user.getId(), user.getEmail(), "sign-up attempt", "Someone tried to sign up with your email", text);
    }

    // logs the user id only: the address is personal data and the text holds the token
    private void sendLimited(UUID userId, String address, String type, String subject, String text) {
        try {
            rateLimiter.consume(RateLimitPolicy.ACCOUNT_MAIL, userId.toString());
        } catch (RateLimitExceededException ex) {
            log.warn("Skipped {} mail to user {}: hourly mail limit reached", type, userId);
            return;
        }

        send(userId, address, type, subject, text);
    }

    private void send(UUID userId, String address, String type, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(address);
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
            log.info("Sent {} mail to user {}", type, userId);
        } catch (MailException ex) {
            log.warn("Failed to send {} mail to user {}: {}", type, userId, ex.toString());
        }
    }
}
