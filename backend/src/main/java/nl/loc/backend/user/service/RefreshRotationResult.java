package nl.loc.backend.user.service;

import nl.loc.backend.user.model.UserAccount;

public record RefreshRotationResult(UserAccount user, String rawRefreshToken) {
}
