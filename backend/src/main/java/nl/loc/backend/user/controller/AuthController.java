package nl.loc.backend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import nl.loc.backend.config.ratelimit.RateLimitPolicy;
import nl.loc.backend.config.ratelimit.RateLimiter;
import nl.loc.backend.user.dto.request.LoginRequest;
import nl.loc.backend.user.dto.request.RegisterRequest;
import nl.loc.backend.user.dto.request.ResendVerificationRequest;
import nl.loc.backend.user.dto.request.VerifyEmailRequest;
import nl.loc.backend.user.dto.response.LoginResponse;
import nl.loc.backend.user.service.EmailVerificationService;
import nl.loc.backend.user.service.LoginService;
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
    private final EmailVerificationService emailVerificationService;
    private final RateLimiter rateLimiter;
    private final LoginService loginService;

    public AuthController(RegistrationService registrationService,
                          EmailVerificationService emailVerificationService,
                          RateLimiter rateLimiter, LoginService loginService) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.rateLimiter = rateLimiter;
        this.loginService = loginService;
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
                    + "A wrong password and an unknown email get the same 401, "
                    + "so the response never reveals which emails have an account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in."),
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
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request);
    }
}
