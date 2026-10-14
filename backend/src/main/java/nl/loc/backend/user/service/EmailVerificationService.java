package nl.loc.backend.user.service;

import nl.loc.backend.user.model.EmailVerificationToken;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.EmailVerificationTokenRepository;
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

    public EmailVerificationService(EmailVerificationTokenRepository emailVerificationTokenRepository, Clock clock) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public String issueToken(UserAccount user) {
        Instant now = Instant.now(clock);
        emailVerificationTokenRepository.invalidateUnused(user, now);

        String rawToken = SecureTokens.generate();
        emailVerificationTokenRepository.save(
                new EmailVerificationToken(user, SecureTokens.hash(rawToken), now, TOKEN_TTL)
        );

        return rawToken;
    }

    @Transactional
    public void verify(String rawToken) {
        Instant now = Instant.now(clock);
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(SecureTokens.hash(rawToken))
                .filter(found -> found.isUsable(now))
                .orElseThrow(InvalidTokenException::new);

        token.markUsed(now);
        token.getUser().markEmailVerified(now);
    }
}
