package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import nl.loc.backend.user.dto.request.ChangeEmailRequest;
import nl.loc.backend.user.service.EmailChangeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailChangeController {
    private final EmailChangeService service;

    public EmailChangeController(EmailChangeService service) {
        this.service = service;
    }

    @PostMapping("/api/me/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a verification link to a new email address")
    public void changeEmail(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangeEmailRequest request) {
        service.request(UUID.fromString(jwt.getSubject()), request.currentPassword(), request.newEmail());
    }
}
