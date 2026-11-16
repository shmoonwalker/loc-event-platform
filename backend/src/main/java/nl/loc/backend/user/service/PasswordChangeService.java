package nl.loc.backend.user.service;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.RefreshTokenRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final AccountSecurityGuard guard;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    @Transactional
    public String change(UUID userId, String currentPassword, String newPassword) {
        UserAccount user = guard.requireCurrentPassword(userId, currentPassword);
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(userId, clock.instant());
        return refreshTokenService.issueToken(user);
    }
}
