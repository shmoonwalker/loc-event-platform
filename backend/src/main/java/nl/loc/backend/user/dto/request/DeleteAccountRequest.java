package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String currentPassword) {

    @Override
    public String toString() {
        return "DeleteAccountRequest[currentPassword=***]";
    }
}
