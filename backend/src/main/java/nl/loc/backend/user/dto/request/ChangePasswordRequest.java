package nl.loc.backend.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = RegisterRequest.MIN_PASSWORD_LENGTH) String newPassword) {

    @Schema(hidden = true)
    @AssertTrue(message = "Password must not exceed 72 bytes")
    public boolean isNewPasswordWithinByteLimit() {
        return RegisterRequest.withinPasswordByteLimit(newPassword);
    }

    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
