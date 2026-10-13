package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import nl.loc.backend.user.dto.request.RegisterRequest;
import nl.loc.backend.user.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration and sign-in.")
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Register",
            description = "Creates an account with email, password and display name. "
                    + "Always returns 202 with no body, also when the email is already registered, "
                    + "so the response never reveals which emails have an account.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Registration accepted."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or malformed JSON.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request.email(), request.password(), request.displayName());
    }
}
