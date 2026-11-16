package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import nl.loc.backend.user.dto.request.DeleteAccountRequest;
import nl.loc.backend.user.service.AccountDeletionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Account security")
public class AccountDeletionController {

    private final AccountDeletionService accounts;

    public AccountDeletionController(AccountDeletionService accounts) {
        this.accounts = accounts;
    }

    @DeleteMapping("/api/me")
    @Operation(summary = "Delete your account", description = "Requires the current password. Comments stay anonymous.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest request) {
        accounts.delete(UUID.fromString(jwt.getSubject()), request.currentPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.clear().toString()).build();
    }
}
