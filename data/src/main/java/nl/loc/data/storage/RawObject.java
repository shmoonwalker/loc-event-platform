package nl.loc.data.storage;

import java.util.Map;

/** The saved payload and its collection metadata, read from the same object. */
public record RawObject(byte[] payload, Map<String, String> metadata) {
    public RawObject {
        metadata = Map.copyOf(metadata);
    }
}
