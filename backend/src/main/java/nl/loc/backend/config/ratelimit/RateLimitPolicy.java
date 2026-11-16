package nl.loc.backend.config.ratelimit;

public enum RateLimitPolicy {
    REGISTER,
    ACCOUNT_MAIL,
    RESEND_VERIFICATION,
    FORGOT_PASSWORD,
    LOGIN,
    LOGIN_EMAIL,
    SESSION,
    COMMENT,
    AVATAR_UPLOAD,
    ACCOUNT_SECURITY
}
