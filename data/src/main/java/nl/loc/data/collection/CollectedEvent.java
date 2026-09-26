package nl.loc.data.collection;

/** Identity comes from the response, not from interpreting a storage filename. */
public record CollectedEvent(String externalId, String rawObjectKey) {
}
