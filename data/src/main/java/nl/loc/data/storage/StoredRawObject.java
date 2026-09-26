package nl.loc.data.storage;

import java.time.Instant;

public record StoredRawObject(String key, Instant lastModified) {
}
