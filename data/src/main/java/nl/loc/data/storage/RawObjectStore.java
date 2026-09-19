package nl.loc.data.storage;

/** Stores original payloads using keys such as raw/amsterdam/<run-id>/events.json. */
public interface RawObjectStore {

    /** Creates an object. Existing objects must not be overwritten. */
    void put(String key, byte[] payload, String contentType);

    byte[] get(String key);
}
