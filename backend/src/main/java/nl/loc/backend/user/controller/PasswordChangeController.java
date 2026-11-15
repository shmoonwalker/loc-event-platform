package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nl.loc.backend.config.jwt.JwtProperties;
import nl.loc.backend.user.dto.request.ChangePasswordRequest;
import nl.loc.backend.user.service.PasswordChangeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PasswordChangeController {

    private final PasswordChangeService passwordChangeService;
    private final JwtProperties jwtProperties;

    @PostMapping("/api/me/password")
    @Operation(summary = "Change your password and sign out other sessions")
    public ResponseEntity<Void> change(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        String token = passwordChangeService.change(UUID.fromString(jwt.getSubject()),
                request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,
                RefreshTokenCookie.create(token, jwtProperties.refreshTokenTtl()).toString()).build();
    }
}
