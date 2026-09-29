package nl.loc.data.tagging;

/** Gemini could not be reached or failed temporarily, so the message deserves a retry. */
public class TaggingUnavailableException extends RuntimeException {

    public TaggingUnavailableException(String message) {
        super(message);
    }

    public TaggingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
