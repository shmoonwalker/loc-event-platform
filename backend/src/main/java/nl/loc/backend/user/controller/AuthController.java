package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import nl.loc.backend.config.ratelimit.RateLimitPolicy;
import nl.loc.backend.config.ratelimit.RateLimiter;
import nl.loc.backend.user.dto.request.LoginRequest;
import nl.loc.backend.user.dto.request.RegisterRequest;
import nl.loc.backend.user.dto.request.ResendVerificationRequest;
import nl.loc.backend.user.dto.request.VerifyEmailRequest;
import nl.loc.backend.user.dto.response.LoginResponse;
import nl.loc.backend.user.dto.response.UserResponse;
import nl.loc.backend.user.model.UserAccount;
import nl.loc.backend.user.service.CurrentUserService;
import nl.loc.backend.user.service.EmailVerificationService;
import nl.loc.backend.user.service.LoginResult;
import nl.loc.backend.user.service.LoginService;
import nl.loc.backend.user.service.RegistrationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final EmailVerificationService emailVerificationService;
    private final RateLimiter rateLimiter;
    private final LoginService loginService;
    private final CurrentUserService currentUserService;

    public AuthController(RegistrationService registrationService,
                          EmailVerificationService emailVerificationService,
                          RateLimiter rateLimiter, LoginService loginService,
                          CurrentUserService currentUserService) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.rateLimiter = rateLimiter;
        this.loginService = loginService;
        this.currentUserService = currentUserService;
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
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many registrations from this IP. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        // before BCrypt, so blocked requests cost almost nothing
        rateLimiter.consume(RateLimitPolicy.REGISTER, httpRequest.getRemoteAddr());
        registrationService.register(request.email(), request.password(), request.displayName());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Resend verification email",
            description = "Sends a new verification link when the email belongs to an unverified account. "
                    + "Always returns 202 with no body, so the response never reveals which emails have an account.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Request accepted."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or malformed JSON.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many requests from this IP. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request,
                                   HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimitPolicy.RESEND_VERIFICATION, httpRequest.getRemoteAddr());
        registrationService.resendVerification(request.email());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Verify email",
            description = "Marks the account's email as verified using the token from the verification link. "
                    + "Unknown, expired and already used tokens all get the same 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Email verified."),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid, expired or used token, invalid input or malformed JSON.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in",
            description = "Checks email and password and returns a short-lived access token. "
                    + "Also sets a refresh_token cookie: HttpOnly, Secure, SameSite=Strict, Path=/api/auth, "
                    + "Max-Age equal to the refresh token TTL. The raw refresh token is not in the JSON body. "
                    + "A wrong password and an unknown email get the same 401, "
                    + "so the response never reveals which emails have an account.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Logged in.",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class)),
                    headers = @Header(
                            name = "Set-Cookie",
                            description = "refresh_token cookie. HttpOnly, Secure, SameSite=Strict, Path=/api/auth. "
                                    + "Max-Age is the refresh token TTL in seconds.",
                            schema = @Schema(
                                    type = "string",
                                    example = "refresh_token=<token>; Path=/api/auth; Max-Age=1209600; "
                                            + "Secure; HttpOnly; SameSite=Strict"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input or malformed JSON.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid email or password.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Correct password, but the email is not verified yet.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many login attempts for this email. Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        // before BCrypt, so blocked requests cost almost nothing; slow down only, never lock the account
        String email = UserAccount.normalizeEmail(request.email());
        rateLimiter.consume(RateLimitPolicy.LOGIN, httpRequest.getRemoteAddr() + ":" + email);
        rateLimiter.consume(RateLimitPolicy.LOGIN_EMAIL, email);
        LoginResult result = loginService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        RefreshTokenCookie.create(result.rawRefreshToken(), result.refreshTokenTtl()).toString())
                .body(result.response());
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh session",
            description = "Reads the refresh_token cookie and returns a new short-lived access token. "
                    + "Also sets a new refresh_token cookie with the same attributes as login "
                    + "(HttpOnly, Secure, SameSite=Strict, Path=/api/auth). The new token keeps the expiry "
                    + "of the login it came from, so a session ends one refresh TTL after login. "
                    + "The raw refresh token is not in the JSON body. "
                    + "Missing, unknown, expired, revoked, and reused cookies all get the same 400, "
                    + "which also clears the cookie. Reusing an already rotated token revokes the whole session.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "New access token issued.",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class)),
                    headers = @Header(
                            name = "Set-Cookie",
                            description = "New refresh_token cookie. HttpOnly, Secure, SameSite=Strict, Path=/api/auth. "
                                    + "Max-Age is the seconds left until the session expires.",
                            schema = @Schema(
                                    type = "string",
                                    example = "refresh_token=<token>; Path=/api/auth; Max-Age=1209600; "
                                            + "Secure; HttpOnly; SameSite=Strict"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Missing, invalid, expired, revoked or reused refresh cookie. "
                            + "Set-Cookie clears refresh_token (Max-Age=0).",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many refresh or logout requests from this IP. "
                            + "Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<LoginResponse> refresh(
            @Parameter(
                    name = RefreshTokenCookie.NAME,
                    in = ParameterIn.COOKIE,
                    description = "Refresh token from login (HttpOnly cookie).",
                    required = true)
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String rawRefreshToken,
            HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimitPolicy.SESSION, httpRequest.getRemoteAddr());
        LoginResult result = loginService.refresh(rawRefreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        RefreshTokenCookie.create(result.rawRefreshToken(), result.refreshTokenTtl()).toString())
                .body(result.response());
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Log out",
            description = "Revokes the refresh token family for the refresh_token cookie when it matches a stored "
                    + "token, then clears the cookie (HttpOnly, Secure, SameSite=Strict, Path=/api/auth). "
                    + "Always returns 204: missing, unknown, or already revoked cookies still clear the browser cookie.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Logged out.",
                    headers = @Header(
                            name = "Set-Cookie",
                            description = "Clears refresh_token. HttpOnly, Secure, SameSite=Strict, Path=/api/auth. "
                                    + "Max-Age=0.",
                            schema = @Schema(
                                    type = "string",
                                    example = "refresh_token=; Path=/api/auth; Max-Age=0; "
                                            + "Secure; HttpOnly; SameSite=Strict"))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many refresh or logout requests from this IP. "
                            + "Retry-After says how many seconds to wait.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> logout(
            @Parameter(
                    name = RefreshTokenCookie.NAME,
                    in = ParameterIn.COOKIE,
                    description = "Refresh token from login or refresh (HttpOnly cookie).",
                    required = false)
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String rawRefreshToken,
            HttpServletRequest httpRequest) {
        rateLimiter.consume(RateLimitPolicy.SESSION, httpRequest.getRemoteAddr());
        loginService.logout(rawRefreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.clear().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(
            summary = "Current user",
            description = "Returns the account of the access token in the Authorization header. Never returns password material.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The logged-in user."),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid or expired access token, or the account no longer exists.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return currentUserService.currentUser(UUID.fromString(jwt.getSubject()));
    }
}
