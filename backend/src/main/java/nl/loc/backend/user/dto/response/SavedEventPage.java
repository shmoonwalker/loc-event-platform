package nl.loc.backend.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One page of the signed-in member's saved events, most recently saved first. page starts at 0.")
public record SavedEventPage(
        @Schema(description = "Zero-based page index.", example = "0", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int page,

        @Schema(description = "Page size.", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
        int size,

        @Schema(description = "Total saved events of the member.", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,

        @Schema(description = "Total pages for this size. Zero when nothing is saved.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages,

        @Schema(description = "Saved events on this page.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<SavedEventResponse> items) {
}
