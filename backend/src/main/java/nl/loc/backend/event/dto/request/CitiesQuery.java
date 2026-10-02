package nl.loc.backend.event.dto.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Query string for GET /api/cities. Empty q lists cities with Amsterdam first.")
public class CitiesQuery {

    @Parameter(
            name = "q",
            in = ParameterIn.QUERY,
            required = false,
            description = "Optional filter on city slug or name. Empty q lists cities with Amsterdam first.",
            example = "amster")
    @Schema(description = "Optional filter on city slug or name.", example = "amster")
    private String q;

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }
}
