package nl.loc.backend.user.controller;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/auth";

    private RefreshTokenCookie() {
    }

    public static ResponseCookie create(String rawToken, Duration maxAge) {
        return baseBuilder(rawToken).maxAge(maxAge).build();
    }

    public static ResponseCookie clear() {
        return baseBuilder("").maxAge(Duration.ZERO).build();
    }

    private static ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH);
    }
}