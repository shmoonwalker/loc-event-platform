package nl.loc.data.collection;

import java.time.Instant;

/** The source query whose absence decisions a completed run can support. */
public record CollectionScope(boolean fullSource, String countryCode, Instant startsAt, Instant endsAt) {
    public static CollectionScope full() {
        return new CollectionScope(true, null, null, null);
    }
}
