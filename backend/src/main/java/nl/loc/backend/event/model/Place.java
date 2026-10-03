package nl.loc.backend.event.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Published {@code location.type}: ONLINE or PHYSICAL. */
@Schema(
        description = "Published location.type. Query values are online and physical.",
        allowableValues = {"online", "physical"})
public enum Place {
    @Schema(description = "location.type ONLINE. The online rail is not filtered by city. Query value: online.")
    ONLINE,

    @Schema(description = "location.type PHYSICAL. City rails and near you use this. Query value: physical.")
    PHYSICAL
}
