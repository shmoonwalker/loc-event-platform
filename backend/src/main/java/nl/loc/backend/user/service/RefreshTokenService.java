package nl.loc.backend.user.service;

import nl.loc.backend.config.jwt.JwtProperties;
import nl.loc.backend.user.model.RefreshToken;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public String issueToken(UserAccount user) {
        Instant now = Instant.now(clock);
        String rawToken = SecureTokens.generate();
        refreshTokenRepository.save(
                new RefreshToken(user, SecureTokens.hash(rawToken), UUID.randomUUID(), now,
                        jwtProperties.refreshTokenTtl())
        );
        return rawToken;
    }

    @Transactional
    public RefreshRotationResult rotate(String rawToken) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        RefreshToken existing = refreshTokenRepository
                .findByTokenHash(SecureTokens.hash(rawToken))
                .orElseThrow(InvalidTokenException::new);

        if (existing.getReplacedBy() != null) {
            refreshTokenRepository.revokeActiveByFamilyId(existing.getFamilyId(), now);
            throw new InvalidTokenException();
        }

        if (!existing.isActive(now)) {
            throw new InvalidTokenException();
        }

        String newRawToken = SecureTokens.generate();
        RefreshToken successor = new RefreshToken(
                existing.getUser(),
                SecureTokens.hash(newRawToken),
                existing.getFamilyId(),
                now,
                jwtProperties.refreshTokenTtl());
        successor = refreshTokenRepository.saveAndFlush(successor);

        existing.markReplacedBy(successor.getId());
        refreshTokenRepository.save(existing);

        return new RefreshRotationResult(existing.getUser(), newRawToken);
    }
}