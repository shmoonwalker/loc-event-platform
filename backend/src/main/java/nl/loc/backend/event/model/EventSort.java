package nl.loc.backend.event.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Sort order. Defaults to relevance when q is present, otherwise start time. Then id.",
        allowableValues = {"start_time", "relevance"})
public enum EventSort {
    @Schema(description = "Order by start time, then id. Query value: start_time.")
    START_TIME,

    @Schema(description = "Order by search rank, then start time, then id. Query value: relevance.")
    RELEVANCE
}
