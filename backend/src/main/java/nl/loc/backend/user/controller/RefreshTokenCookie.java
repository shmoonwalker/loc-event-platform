package nl.loc.backend.user.controller;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/auth";

    private RefreshTokenCookie() {
    }

    public static ResponseCookie create(String rawToken, Duration maxAge) {
        return ResponseCookie.from(NAME, rawToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}