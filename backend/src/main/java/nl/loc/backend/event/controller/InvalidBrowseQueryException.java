package nl.loc.backend.event.controller;

public class InvalidBrowseQueryException extends RuntimeException {

    public InvalidBrowseQueryException(String message) {
        super(message);
    }
}
