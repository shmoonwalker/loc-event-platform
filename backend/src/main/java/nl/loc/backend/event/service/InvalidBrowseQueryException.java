package nl.loc.backend.event.service;

public class InvalidBrowseQueryException extends RuntimeException {

    public InvalidBrowseQueryException(String message) {
        super(message);
    }
}
