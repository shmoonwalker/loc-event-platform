package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Equal-sized homepage preview. {@code hasMore} is true when {@code total} exceeds {@code items}. */
@Schema(description = "Homepage rail preview. Each rail returns at most 6 items, plus the full total.")
public record EventRail(
        @Schema(description = "Total events matching this rail, not only the preview.") long total,
        @Schema(description = "Preview items. At most 6.") List<EventCard> items,
        @Schema(description = "True when total exceeds the preview. See-all uses GET /api/events, page 0, size at most 20.")
        boolean hasMore
) {
}
