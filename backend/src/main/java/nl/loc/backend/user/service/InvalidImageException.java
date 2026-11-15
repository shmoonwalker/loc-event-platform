package nl.loc.backend.user.service;

public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
