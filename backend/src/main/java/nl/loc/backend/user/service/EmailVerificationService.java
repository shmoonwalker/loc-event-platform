package nl.loc.backend.user.service;

import nl.loc.backend.user.model.EmailVerificationToken;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.EmailVerificationTokenRepository;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class EmailVerificationService {

    static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final Clock clock;

    private final UserAccountRepository users;
    private final ApplicationEventPublisher events;

    public EmailVerificationService(EmailVerificationTokenRepository emailVerificationTokenRepository, Clock clock,
                                    UserAccountRepository users, ApplicationEventPublisher events) {
        this.users = users;
        this.events = events;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public String issueToken(UserAccount user) {
        return issueToken(user, null);
    }

    void invalidateUnused(UserAccount user) {
        emailVerificationTokenRepository.invalidateUnused(user, clock.instant());
    }

    String issueToken(UserAccount user, String newEmail) {
        Instant now = Instant.now(clock);
        emailVerificationTokenRepository.invalidateUnused(user, now);

        String rawToken = SecureTokens.generate();
        emailVerificationTokenRepository.save(
                new EmailVerificationToken(user, SecureTokens.hash(rawToken), now, TOKEN_TTL, newEmail)
        );

        return rawToken;
    }

    @Transactional
    public void verify(String rawToken) {
        Instant now = Instant.now(clock);
        byte[] hash = SecureTokens.hash(rawToken);
        // Requests and confirmations both lock the account before touching its tokens.
        UserAccount user = emailVerificationTokenRepository.findOwnerByTokenHash(hash)
                .flatMap(users::findLockedById)
                .orElseThrow(InvalidTokenException::new);
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(hash)
                .filter(found -> found.isUsable(now))
                .orElseThrow(InvalidTokenException::new);

        String newEmail = token.getNewEmail();
        token.markUsed(now);
        if (newEmail == null) {
            user.markEmailVerified(now);
            return;
        }

        String oldEmail = user.getEmail();
        user.changeVerifiedEmail(newEmail, now);
        try {
            // A registration or another confirmation can claim the address after the request.
            users.flush();
        } catch (DataIntegrityViolationException ex) {
            if (RegistrationService.isDuplicateEmail(ex)) {
                throw new InvalidTokenException();
            }
            throw ex;
        }
        events.publishEvent(new EmailChangeService.EmailChanged(user.getId(), oldEmail));
    }
}
