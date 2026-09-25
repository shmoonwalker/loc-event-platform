package nl.loc.data.collection;

import java.util.List;

public interface SourceCollector {

    String source();

    List<String> collect();
}
