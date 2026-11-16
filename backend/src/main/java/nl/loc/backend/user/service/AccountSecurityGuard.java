package nl.loc.backend.user.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.loc.backend.config.ratelimit.RateLimitPolicy;
import nl.loc.backend.config.ratelimit.RateLimiter;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.repository.UserAccountRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountSecurityGuard {

    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final RateLimiter rateLimiter;

    // The caller's transaction holds the lock through the account change.
    public UserAccount requireCurrentPassword(UUID userId, String currentPassword) {
        rateLimiter.consume(RateLimitPolicy.ACCOUNT_SECURITY, userId.toString());
        UserAccount user = users.findLockedById(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid current password"));
        if (user.getPasswordHash() == null || !passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid current password");
        }
        return user;
    }
}
