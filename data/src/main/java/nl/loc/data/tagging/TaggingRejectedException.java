package nl.loc.data.tagging;

/** Gemini refused the request in a way that repeating it cannot fix. */
public class TaggingRejectedException extends RuntimeException {

    public TaggingRejectedException(String message) {
        super(message);
    }
}
