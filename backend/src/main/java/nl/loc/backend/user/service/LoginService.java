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

    public LoginService(AuthenticationManager authenticationManager, AccessTokenService accessTokenService, JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.accessTokenService = accessTokenService;
        this.jwtProperties = jwtProperties;
    }

    public LoginResponse login(LoginRequest request) {
        // throws BadCredentialsException for a wrong password and for an unknown email alike
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        UserAccount user = ((AccountUserDetails) authentication.getPrincipal()).account();

        // only after the password is proven, so this can't be used to probe for unverified accounts
        if (!user.isEmailVerified()) {
            log.info("Login refused for unverified account {}", user.getId());
            throw new EmailNotVerifiedException();
        }

        log.info("Account {} logged in", user.getId());
        return new LoginResponse(accessTokenService.issueAccessToken(user), jwtProperties.accessTokenTtl());
    }
}
