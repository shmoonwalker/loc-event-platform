package nl.loc.backend.event.dto.request;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Query for GET /api/home. city scopes the near-you rail only and defaults to amsterdam.")
public class HomeQuery {

    @Parameter(
            name = "city",
            in = ParameterIn.QUERY,
            required = false,
            description = "City slug for the nearYou rail only. Does not filter tags, categories, tonight, "
                    + "this weekend, or online. Defaults to amsterdam. No device location. "
                    + "Invalid slug format returns 400.",
            example = "amsterdam")
    @Schema(description = "Near-you city slug. Defaults to amsterdam. Does not filter the rest of the homepage.",
            example = "amsterdam")
    private String city;

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
