package nl.loc.data.collection;

import java.util.List;
import java.time.Instant;

public interface SourceCollector {

    String source();

    CollectionScope scope(Instant startedAt);

    List<CollectedEvent> collect(CollectionRun run);
}
