package nl.loc.backend.user.service;

import nl.loc.backend.config.jwt.JwtProperties;
import nl.loc.backend.user.model.RefreshToken;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
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

    // commit the family revoke on reuse; the default rollback on a RuntimeException would undo it
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshRotationResult rotate(String rawToken) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        RefreshToken existing = refreshTokenRepository
                .findByTokenHash(SecureTokens.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.getReplacedBy() != null) {
            refreshTokenRepository.revokeActiveByFamilyId(existing.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        if (!existing.isActive(now)) {
            throw new InvalidRefreshTokenException();
        }

        // the successor keeps the family's expiry, so a session ends one TTL after login however often it refreshes
        Duration remainingTtl = Duration.between(now, existing.getExpiresAt());
        String newRawToken = SecureTokens.generate();
        RefreshToken successor = new RefreshToken(
                existing.getUser(),
                SecureTokens.hash(newRawToken),
                existing.getFamilyId(),
                now,
                remainingTtl);
        successor = refreshTokenRepository.saveAndFlush(successor);

        existing.markReplacedBy(successor.getId());
        refreshTokenRepository.save(existing);

        return new RefreshRotationResult(existing.getUser(), newRawToken, remainingTtl);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        refreshTokenRepository
                .findByTokenHash(SecureTokens.hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeActiveByFamilyId(token.getFamilyId(), now));
    }
}
