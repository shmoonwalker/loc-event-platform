package nl.loc.backend.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import nl.loc.backend.user.mail.AccountMailer;
import nl.loc.backend.user.model.PasswordResetToken;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.PasswordResetTokenRepository;
import nl.loc.backend.user.repository.RefreshTokenRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final PasswordResetTokenRepository tokens;
    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AccountMailer mailer;
    private final Clock clock;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserAccountRepository users,
                                RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                                ApplicationEventPublisher events, AccountMailer mailer, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.mailer = mailer;
        this.clock = clock;
    }

    // unknown emails get nothing, so callers can't tell the cases apart
    @Transactional
    public void requestReset(String email) {
        users.findByEmail(UserAccount.normalizeEmail(email))
                .flatMap(found -> users.findLockedById(found.getId()))
                .ifPresent(user -> {
                    Instant now = clock.instant();
                    // only the latest link works
                    tokens.invalidateUnused(user, now);
                    String rawToken = SecureTokens.generate();
                    tokens.save(new PasswordResetToken(user, SecureTokens.hash(rawToken), now, TOKEN_TTL));
                    log.info("Issued password reset link for account {}", user.getId());
                    events.publishEvent(new LinkRequested(user.getId(), user.getEmail(), rawToken));
                });
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        Instant now = clock.instant();
        byte[] hash = SecureTokens.hash(rawToken);
        // requests and resets both lock the account before touching its tokens
        UserAccount user = tokens.findOwnerByTokenHash(hash)
                .flatMap(users::findLockedById)
                .orElseThrow(InvalidTokenException::new);
        PasswordResetToken token = tokens.findByTokenHash(hash)
                .filter(found -> found.isUsable(now))
                .orElseThrow(InvalidTokenException::new);

        token.markUsed(now);
        user.changePassword(passwordEncoder.encode(newPassword));
        // the link proves the user controls the inbox
        user.markEmailVerified(now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        log.info("Reset password of account {}", user.getId());
    }

    record LinkRequested(UUID userId, String address, String token) {}

    // after commit, so the link never arrives before its token is stored
    @TransactionalEventListener
    public void sendLink(LinkRequested request) {
        mailer.sendPasswordResetLink(request.userId(), request.address(), request.token());
    }
}
