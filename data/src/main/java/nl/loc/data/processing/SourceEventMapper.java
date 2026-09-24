package nl.loc.data.processing;

import java.util.List;

import nl.loc.data.event.NormalizedEvent;

public interface SourceEventMapper {

    String source();

    List<NormalizedEvent> map(String rawJson, String rawObjectKey);
}
