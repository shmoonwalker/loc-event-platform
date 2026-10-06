package nl.loc.backend.event.dto.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Query for GET /api/home. city scopes the near-you rail only and defaults to amsterdam.")
public record HomeQuery(
        @Parameter(
                name = "city",
                in = ParameterIn.QUERY,
                required = false,
                description = "City slug that picks the city for the near-you rail only; the other rails "
                        + "ignore it. Defaults to amsterdam. No device location. Invalid slug format returns 400.",
                example = "amsterdam")
        @Schema(description = "City for the near-you rail only. Defaults to amsterdam.",
                example = "amsterdam")
        String city
) {
}
