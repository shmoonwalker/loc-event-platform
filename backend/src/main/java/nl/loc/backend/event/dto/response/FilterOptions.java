package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Complete supported filter vocabulary, independent of current result counts. Relevance requires search text to be useful.")
public record FilterOptions(
        List<FilterOption> categories,
        List<FilterOption> tags,
        List<FilterOption> sorts,
        List<FilterOption> datePresets,
        List<FilterOption> places,
        String timezone,
        int defaultPageSize,
        int maxPageSize,
        int maxQueryLength
) {
}
