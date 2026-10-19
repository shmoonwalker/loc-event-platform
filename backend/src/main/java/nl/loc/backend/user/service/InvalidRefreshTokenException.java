package nl.loc.backend.user.service;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid, expired or revoked refresh token");
    }
}
