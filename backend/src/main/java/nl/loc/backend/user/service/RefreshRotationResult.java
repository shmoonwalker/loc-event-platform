package nl.loc.backend.user.service;

import java.time.Duration;
import nl.loc.backend.user.model.UserAccount;

public record RefreshRotationResult(UserAccount user, String rawRefreshToken, Duration remainingTtl) {
}
