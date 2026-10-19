package nl.loc.backend.user.service;

import java.time.Duration;
import nl.loc.backend.user.dto.response.LoginResponse;

public record LoginResult(LoginResponse response, String rawRefreshToken, Duration refreshTokenTtl) {
}