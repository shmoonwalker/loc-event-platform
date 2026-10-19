package nl.loc.backend.user.service;

public class RefreshTokenAlreadyRotatedException extends RuntimeException {
    public RefreshTokenAlreadyRotatedException() {
        super("Refresh token was rotated moments ago by another request");
    }
}
