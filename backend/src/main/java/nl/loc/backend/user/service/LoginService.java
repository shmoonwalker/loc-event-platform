package nl.loc.backend.user.service;

import lombok.extern.slf4j.Slf4j;
import nl.loc.backend.config.jwt.JwtProperties;
import nl.loc.backend.user.dto.request.LoginRequest;
import nl.loc.backend.user.dto.response.LoginResponse;
import nl.loc.backend.user.model.UserAccount;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final AccessTokenService accessTokenService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;

    public LoginService(AuthenticationManager authenticationManager, AccessTokenService accessTokenService, JwtProperties jwtProperties, RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.accessTokenService = accessTokenService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
    }

    public LoginResult login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        UserAccount user = ((AccountUserDetails) authentication.getPrincipal()).account();

        if (!user.isEmailVerified()) {
            log.info("Login refused for unverified account {}", user.getId());
            throw new EmailNotVerifiedException();
        }

        log.info("Account {} logged in", user.getId());
        String rawRefreshToken = refreshTokenService.issueToken(user);
        return new LoginResult(
                new LoginResponse(accessTokenService.issueAccessToken(user), jwtProperties.accessTokenTtl()),
                rawRefreshToken,
                jwtProperties.refreshTokenTtl());
    }

    public LoginResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshRotationResult rotation = refreshTokenService.rotate(rawRefreshToken);
        UserAccount user = rotation.user();
        return new LoginResult(
                new LoginResponse(accessTokenService.issueAccessToken(user), jwtProperties.accessTokenTtl()),
                rotation.rawRefreshToken(),
                rotation.remainingTtl());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.logout(rawRefreshToken);
    }
}
