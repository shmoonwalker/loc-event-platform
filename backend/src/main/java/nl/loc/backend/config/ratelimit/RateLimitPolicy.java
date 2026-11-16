package nl.loc.backend.config.ratelimit;

public enum RateLimitPolicy {
    REGISTER,
    ACCOUNT_MAIL,
    RESEND_VERIFICATION,
    LOGIN,
    LOGIN_EMAIL,
    SESSION,
    COMMENT,
    REPORT,
    AVATAR_UPLOAD,
    ACCOUNT_SECURITY
}
