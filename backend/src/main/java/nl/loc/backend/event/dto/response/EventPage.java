package nl.loc.backend.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of GET /api/events. page starts at 0. size is at most 20.")
public record EventPage(
        @Schema(description = "Zero-based page index.", example = "0", minimum = "0") int page,
        @Schema(
                description = "Page size. Defaults to 20 on the collection and cannot exceed 20.",
                example = "20",
                maximum = "20")
        int size,
        @Schema(description = "Total matching events.") long totalElements,
        @Schema(description = "Total pages for this size. Zero when nothing matches.") int totalPages,
        @Schema(description = "Events on this page.") List<EventCard> items
) {
}
