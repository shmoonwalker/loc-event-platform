package nl.loc.backend.user.mail;

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
        String link = UriComponentsBuilder
                .fromUriString(properties.frontendUrl())
                .path("/verify-email")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        String text = """
                Welcome to Loc!

                Verify your email address using this link:
                %s

                This link is valid for 24 hours.

                If you didn't create this account, you can ignore this email.
                """.formatted(link);

        send(user, "verification", "Verify your email address", text);
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

        send(user, "sign-up attempt", "Someone tried to sign up with your email", text);
    }

    // logs the user id only: the address is personal data and the text holds the token
    private void send(UserAccount user, String type, String subject, String text) {
        try {
            rateLimiter.consume(RateLimitPolicy.ACCOUNT_MAIL, user.getId().toString());
        } catch (RateLimitExceededException ex) {
            log.warn("Skipped {} mail to user {}: hourly mail limit reached", type, user.getId());
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
            log.info("Sent {} mail to user {}", type, user.getId());
        } catch (MailException ex) {
            log.warn("Failed to send {} mail to user {}: {}", type, user.getId(), ex.toString());
        }
    }
}
