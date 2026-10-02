package nl.loc.backend.event.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Time window preset. Query values are tonight, weekend, and upcoming.",
        allowableValues = {"tonight", "weekend", "upcoming"})
public enum When {
    @Schema(description = "Amsterdam calendar day, local midnight to the next midnight. Query value: tonight.")
    TONIGHT,

    @Schema(description = "Saturday 00:00 through Monday 00:00 in Europe/Amsterdam. Query value: weekend.")
    WEEKEND,

    @Schema(description = "The next 30 days. Query value: upcoming.")
    UPCOMING
}
