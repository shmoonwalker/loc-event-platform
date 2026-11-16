package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
        @NotBlank @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY) String currentPassword,
        @NotBlank @Email @Size(max = 254) String newEmail) {

    @Override
    public String toString() {
        return "ChangeEmailRequest[currentPassword=***, newEmail=***]";
    }
}
