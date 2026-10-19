package nl.loc.backend.config;

import nl.loc.backend.config.ratelimit.RateLimitExceededException;
import nl.loc.backend.event.controller.InvalidBrowseQueryException;
import nl.loc.backend.user.controller.RefreshTokenCookie;
import nl.loc.backend.user.service.AccountNotFoundException;
import nl.loc.backend.user.service.EmailNotVerifiedException;
import nl.loc.backend.user.service.InvalidRefreshTokenException;
import nl.loc.backend.user.service.InvalidTokenException;
import nl.loc.backend.user.service.RefreshTokenAlreadyRotatedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Missing required request parameter");
        problem.setDetail("Required parameter '" + ex.getParameterName() + "' is not present.");
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request parameter");
        problem.setDetail("The value provided for '" + ex.getName() + "' has an invalid format");
        return problem;
    }

    @ExceptionHandler(InvalidBrowseQueryException.class)
    public ProblemDetail handleInvalidBrowseQuery(InvalidBrowseQueryException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid browse query");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid token");
        problem.setDetail("This link is invalid or has expired");
        return problem;
    }

    // also clears the cookie, so the browser stops sending a token that will never work again
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid refresh token");
        problem.setDetail("Your session has expired, please log in again");
        return ResponseEntity.badRequest()
                .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.clear().toString())
                .body(problem);
    }

    // keeps the cookie: the request that won the race has just set the new one
    @ExceptionHandler(RefreshTokenAlreadyRotatedException.class)
    public ProblemDetail handleRefreshTokenAlreadyRotated(RefreshTokenAlreadyRotatedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Session already refreshed");
        problem.setDetail("Another request refreshed this session just now; retry the refresh once");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed request body");
        problem.setDetail("The request body is missing or is not valid JSON");
        return problem;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle("Too many requests");
        problem.setDetail("Please try again in " + ex.getRetryAfterSeconds() + " seconds");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.merge(
                error.getField(),
                Objects.requireNonNullElse(error.getDefaultMessage(), ""),
                (first, second) -> first + "; " + second));
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid credentials");
        problem.setDetail("Invalid email or password");
        return problem;
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ProblemDetail handleEmailNotVerified(EmailNotVerifiedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Email not verified");
        problem.setDetail("Please verify your email address before logging in");
        return problem;
    }

    // same answer as an invalid token: the token no longer belongs to an account
    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        return SecurityProblemHandler.unauthorized();
    }
}
