package nl.loc.data.storage;

import java.util.Map;

/** Stores original payloads using keys such as raw/<source>/<run-id>/events.json. */
public interface RawObjectStore {

    /** Creates an object. Existing objects must not be overwritten. */
    void put(String key, byte[] payload, String contentType);

    /** Creates an object with optional user metadata. Existing objects must not be overwritten. */
    void put(String key, byte[] payload, String contentType, Map<String, String> metadata);

    RawObject get(String key);
}
