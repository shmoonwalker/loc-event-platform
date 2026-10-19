# Authentication

How users register with Loc, verify their email, log in, stay logged in and log out, how passwords and tokens are stored, how requests are authorized, and how the authentication endpoints are protected against abuse.

---

## Principles

- **No account enumeration.** No response, error or timing difference reveals whether an email is registered. Only the owner of the inbox learns what happened, by email.
- **Spring Security first.** Its building blocks (password encoder, security filter chain, `AuthenticationManager`, OAuth2 resource server) are used before any custom security code.
- **Secrets are stored only as hashes.** Passwords use BCrypt; random tokens use SHA-256.
- **Endpoints that hash passwords or send email are rate limited.**
- **One error format.** Every error response is an RFC 7807 `ProblemDetail`.
- **Deny by default.** The security chain rejects every route that is not explicitly opened, and each auth route is opened on its own. `/api/auth/**` is never opened as a whole.

---

## Registration

`POST /api/auth/register`

```json
{ "email": "anouk.devries@example.com", "password": "correct-horse-battery", "displayName": "Anouk de Vries" }
```

1. The client IP is checked against the register rate limit. Over the limit returns `429`.
2. The body is validated. Invalid input or malformed JSON returns `400`.
3. The email is normalised and the password is hashed. The hash is computed **before** the duplicate check, so new and existing emails take the same time.
4. What happens next depends on the account state:

| Account state | Account change | Email sent |
|---|---|---|
| New | User created with role `USER`, unverified | Verification link |
| Exists, unverified | Password and display name replaced | Fresh verification link |
| Exists, verified | None | "Someone tried to sign up with your address", with a link to password reset |

5. The response is always `202 Accepted` with an empty body: no id and no account details, so the response never reveals which emails have an account.

An unverified account takes the password of the latest registration, so whoever registered the address first cannot keep their password on the account the owner later verifies.

Two simultaneous registrations for the same email are resolved by the database: the unique constraint `app_user_email_key` rejects the second insert, and that violation is treated like an existing email; the winning registration sends the mail. Any other constraint violation is a bug and surfaces as an error.

### Validation

| Field | Rules |
|---|---|
| `email` | Required, valid email address, at most 254 characters |
| `password` | Required, at least 10 characters, at most 72 bytes in UTF-8 |
| `displayName` | Required, not blank, at most 100 characters |

Validation errors return a `ProblemDetail` with an `errors` map of field name to message.

### Code

| Class | Role |
|---|---|
| `user/controller/AuthController` | HTTP endpoint; applies the rate limit, then calls the service |
| `user/dto/request/RegisterRequest` | Request body and its validation rules |
| `user/service/RegistrationService` | Normalises, hashes, checks for duplicates, saves and triggers the email |
| `user/repository/UserAccountRepository` | Spring Data JPA repository for `app_user` |
| `user/model/UserAccount` | JPA entity |

---

## Email verification

`POST /api/auth/verify-email`

```json
{ "token": "<token from the link>" }
```

The verification email links to the frontend page `FRONTEND_URL/verify-email?token=…`, which posts the token to this endpoint. A plain `GET` link never verifies, because mail security scanners open links and would use up the token.

1. The body is validated: `token` is required and at most 64 characters. Invalid input or malformed JSON returns `400`.
2. The token is hashed and looked up, with a row lock so two parallel requests cannot both use it.
3. A token that is unknown, expired or already used returns `400` with the title `Invalid token`. All three cases get the same response.
4. Otherwise the token is marked used, the account is marked verified, and the response is `204 No Content`.

### Resending the link

`POST /api/auth/resend-verification`

```json
{ "email": "anouk.devries@example.com" }
```

1. The client IP is checked against the resend rate limit. Over the limit returns `429`.
2. The body is validated with the same email rules as registration. Invalid input or malformed JSON returns `400`.
3. If the normalised email belongs to an unverified account, a new token is issued and a fresh link is emailed. Unknown and verified emails get no email.
4. The response is always `202 Accepted` with an empty body, so it never reveals which emails have an account.

### Tokens

- 32 random bytes from `SecureRandom`, encoded as URL-safe Base64 without padding (43 characters).
- Only the SHA-256 hash is stored; the raw token exists only in the email.
- Single use and valid for 24 hours.
- Issuing a new token marks the user's older unused tokens as used, so only the latest link works.

### Sending mail

- `AccountMailer` sends plain-text emails through Spring's `JavaMailSender`.
- Mail is sent `@Async`, after the database step has committed. A slow or failing mail server neither breaks registration nor makes new and existing emails take different time.
- One user receives at most 3 emails per hour, whatever triggers them (`ACCOUNT_MAIL` rate limit, keyed on the user id). Further emails are skipped and logged as a warning; the HTTP response does not change, so the limit reveals nothing about the account.
- A failed send is logged with the user id only and is not retried. The address is personal data and the message holds the token, so neither is logged.
- Local development sends to Mailpit (`docker-compose.yml`, web UI on port 8025); no real email leaves the machine. Production sends through Resend's SMTP server.

### Code

| Class | Role |
|---|---|
| `user/controller/AuthController` | HTTP endpoints for verify-email and resend-verification |
| `user/dto/request/VerifyEmailRequest` | Verify-email request body and its validation rules |
| `user/dto/request/ResendVerificationRequest` | Resend request body and its validation rules |
| `user/service/RegistrationService` | Decides whether a resend sends a new link |
| `user/service/EmailVerificationService` | Issues and verifies tokens |
| `user/service/SecureTokens` | Generates random tokens and hashes them |
| `user/service/InvalidTokenException` | Unknown, expired or used token; becomes `400` |
| `user/repository/EmailVerificationTokenRepository` | Spring Data JPA repository for `email_verification_token` |
| `user/model/EmailVerificationToken` | JPA entity |
| `user/mail/AccountMailer` | Builds the links and sends the emails |

---

## Login

`POST /api/auth/login`

```json
{ "email": "anouk.devries@example.com", "password": "correct-horse-battery" }
```

1. The login rate limits are checked: per client IP and email, and per email across all IPs. Over either limit returns `429`.
2. The body is validated: both fields are required, and the email is at most 254 characters. The password rules of registration are not applied, so a later change to those rules never locks out existing accounts. Invalid input or malformed JSON returns `400`.
3. Spring Security's `AuthenticationManager` (a `DaoAuthenticationProvider`) loads the account by normalised email and checks the password with the password encoder.
   - A wrong password, an unknown email and an account without a password all return the same `401` with the title `Invalid credentials`.
   - For an unknown email the provider still runs a BCrypt check against a dummy hash, so a missing account does not answer faster.
4. Only after the password is proven, an unverified account returns `403` with the title `Email not verified`. Checking this before the password would let anyone find unverified accounts. For the same reason `UserDetails.isEnabled()` always returns `true`: `DaoAuthenticationProvider` checks it before the password.
5. Otherwise the response is `200` with an access token, and a refresh token is set as a cookie (see [Sessions](#sessions)):

```json
{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 900 }
```

Successful and refused logins are logged with the account id only. Failed logins are not logged with the email, because a mistyped address can belong to someone else.

### Access tokens

- A JWT signed with HS256 using `JWT_SECRET`, through Spring Security's `NimbusJwtEncoder` and `NimbusJwtDecoder`.
- Valid for `JWT_ACCESS_TOKEN_TTL` (15 minutes by default).
- Claims: `sub` (the account id), `roles` (for example `["USER"]`), `iat` and `exp`. The token holds no email, name or password material: a JWT is signed, not encrypted, so anyone holding it can read it.
- `JWT_SECRET` must be Base64 of at least 32 bytes (256 bits). The application refuses to start when it is missing or too short.
- The role is read when the token is issued. A role change in the database takes effect at the next login, at most one token lifetime later.

### Current user

`GET /api/auth/me` with `Authorization: Bearer <accessToken>` returns the account of the token:

```json
{ "id": "0190a6b2-…", "email": "anouk.devries@example.com", "displayName": "Anouk de Vries", "role": "USER" }
```

It never returns password material. When the account no longer exists, the response is the same `401` as for an invalid token.

### Code

| Class | Role |
|---|---|
| `user/controller/AuthController` | HTTP endpoints for login and the current user; applies the login rate limits |
| `user/dto/request/LoginRequest` | Login request body and its validation rules |
| `user/dto/response/LoginResponse` | Access token, token type and lifetime in seconds |
| `user/dto/response/UserResponse` | The current user, without password material |
| `user/service/LoginService` | Authenticates through the `AuthenticationManager`, checks email verification, issues the access and refresh tokens |
| `user/service/AccountUserDetailsService` | Loads an account by normalised email for Spring Security |
| `user/service/AccountUserDetails` | Adapts `UserAccount` to Spring Security's `UserDetails` |
| `user/service/AccessTokenService` | Builds and signs the access token |
| `user/service/CurrentUserService` | Loads the account of the current token |
| `user/service/EmailNotVerifiedException` | Correct password on an unverified account; becomes `403` |
| `user/service/AccountNotFoundException` | Token of a deleted account; becomes `401` |
| `config/jwt/JwtProperties` | Secret and token lifetimes; validates the secret at startup |
| `config/jwt/JwtConfig` | JWT encoder and decoder beans |

---

## Sessions

A login starts a session: a **family** of refresh tokens. The short-lived access token is renewed with the refresh token, which is rotated on every use.

### Refresh token cookie

- 32 random bytes from `SecureRandom`, URL-safe Base64 (the same generator as verification tokens). Only the SHA-256 hash is stored.
- Sent only as a cookie, never in a JSON body: `refresh_token`, `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`.
  - `HttpOnly`: page scripts cannot read it, so an XSS bug cannot steal it.
  - `SameSite=Strict`: other sites cannot make the browser send it, so `/refresh` and `/logout` need no CSRF token.
  - `Path=/api/auth`: the browser sends it only to the auth routes, not with every API call.
- `Max-Age` is the time left in the session.
- The session ends `JWT_REFRESH_TOKEN_TTL` (14 days by default) after login, however often it is refreshed: every token in a family has the expiry of the login token. A stolen token therefore cannot keep a session alive forever.

### Refreshing

`POST /api/auth/refresh` with the cookie, no body.

1. The client IP is checked against the `SESSION` rate limit. Over the limit returns `429`.
2. The token is hashed and looked up, with a row lock so two parallel requests are handled one after the other.
3. What happens next depends on the token:

| Token | Response | Cookie |
|---|---|---|
| Active | `200` with a new access token; the token is marked replaced and a new one in the same family is issued | New refresh token |
| Missing, unknown, expired or revoked | `400` with the title `Invalid refresh token` | Cleared |
| Already replaced, more than 10 seconds ago | **Reuse:** every token in the family is revoked, then `400` as above | Cleared |
| Already replaced, less than 10 seconds ago | `409` with the title `Session already refreshed` | Unchanged |

- **Reuse detection.** A replaced token should never come back. When it does, either the user or an attacker holds a copy, and the server cannot tell which. Revoking the whole family logs both out; the user logs in again. The revoke is committed even though the request fails. Reuse is logged as a warning with the family id.
- **Parallel refreshes.** Two tabs that refresh at the same moment send the same cookie. The first one rotates it; the second finds it replaced a moment ago. Within the 10-second grace window this is not treated as reuse: the second gets `409`, no new token, and the cookie the first request set stays. The client retries the refresh once, now with the new cookie. If the retry fails as well, the client should call logout, which revokes the family.

### Logging out

`POST /api/auth/logout` with the cookie, no body.

1. The client IP is checked against the `SESSION` rate limit. Over the limit returns `429`.
2. When the cookie matches a stored token, every token in its family is revoked. Other sessions of the same user stay logged in.
3. The response is always `204` and clears the cookie, also for a missing, unknown or already revoked token.

An access token issued before logout or before a reuse revoke stays valid until it expires, at most `JWT_ACCESS_TOKEN_TTL`.

### Cleanup

Every night at 03:30 (Amsterdam time) the backend deletes expired refresh tokens. Because a family shares one expiry, an expired token never belongs to a live session, so deleting it cannot hide a reuse.

### Code

| Class | Role |
|---|---|
| `user/controller/AuthController` | HTTP endpoints for refresh and logout; applies the `SESSION` rate limit and sets the cookie |
| `user/controller/RefreshTokenCookie` | Builds and clears the cookie with its attributes |
| `user/service/LoginService` | Issues a new access token after a rotation |
| `user/service/RefreshTokenService` | Issues, rotates and revokes refresh tokens; detects reuse; nightly cleanup |
| `user/service/InvalidRefreshTokenException` | Missing, unknown, expired, revoked or reused token; becomes `400` and clears the cookie |
| `user/service/RefreshTokenAlreadyRotatedException` | Parallel refresh within the grace window; becomes `409` |
| `user/repository/RefreshTokenRepository` | Spring Data JPA repository for `refresh_token` |
| `user/model/RefreshToken` | JPA entity |
| `config/SchedulingConfig` | Enables `@Scheduled` for the cleanup |

---

## Authorization

Every request passes Spring Security's filter chain (`config/SecurityConfig`):

- `Authorization: Bearer <token>` is validated by the OAuth2 resource server with the same key: signature, `exp` and `nbf` (with 60 seconds of clock skew).
- The `roles` claim becomes Spring authorities with the `ROLE_` prefix, so `["ADMIN"]` becomes `ROLE_ADMIN` and matches `hasRole("ADMIN")`.
- The token's `sub` is the principal name.

| Routes | Access |
|---|---|
| Public discovery `GET` routes, `/api/docs/**` | Everyone |
| `POST /api/auth/register`, `/verify-email`, `/resend-verification`, `/login`, `/refresh`, `/logout` | Everyone; refresh and logout are authorised by the refresh cookie |
| `GET /api/auth/me` | Any valid access token |
| `/api/admin/**` | Role `ADMIN` |
| Anything else | Denied |

- A missing, malformed, expired or wrongly signed token gets the same `401` with the title `Unauthorized` and a `WWW-Authenticate: Bearer` header, so the response does not reveal which check failed.
- A valid token without the required role gets `403` with the title `Forbidden`.
- A request to a public route that carries an invalid token also gets `401`; clients should drop an expired token instead of sending it.
- Errors from the security filters never reach `@RestControllerAdvice`. `config/SecurityProblemHandler` writes them as `ProblemDetail`, and is registered both as the chain's entry point and as the resource server's entry point, which handles bad tokens.

---

## Passwords and emails

- **Encoder:** `DelegatingPasswordEncoder` with `bcrypt` (strength 12) as the only and default encoder. Hashes are stored as `{bcrypt}$2a$12$…`; the prefix allows a later algorithm change without breaking stored passwords.
- **Length limit in bytes:** BCrypt uses only the first 72 bytes, and Spring Security rejects longer input with an exception. The limit is therefore checked in bytes, so a password with multi-byte characters (such as `€` or emoji) gets a `400` instead of causing a `500`.
- **Emails** are trimmed and lower-cased (`UserAccount.normalizeEmail`) before every write and lookup. `Sam@x.com` and `sam@x.com` are the same account.

---

## Data model

Authentication tables live in the backend-owned `app` schema, migrated by Flyway with its own history table `app.flyway_backend_history`.

### `app.app_user`

| Column | Notes |
|---|---|
| `id` | UUIDv7, generated by Hibernate; the database default `uuidv7()` covers manual inserts |
| `email` | Unique; a CHECK keeps it lower-case, which makes uniqueness case-insensitive |
| `password_hash` | Nullable, for accounts that only use Google |
| `display_name` | 1–100 characters after trimming |
| `role` | `USER` or `ADMIN`; new accounts are always `USER` |
| `created_at` | Set by the application from the injected `Clock` |
| `email_verified_at` | `null` while the email is unverified |

Administrators are promoted manually in SQL. Registration never accepts a role.

### `app.email_verification_token`

| Column | Notes |
|---|---|
| `id` | UUIDv7 |
| `user_id` | The account; tokens are deleted with it |
| `token_hash` | Unique SHA-256 hash, exactly 32 bytes |
| `expires_at` | 24 hours after issue |
| `used_at` | Set when the token is used or replaced by a newer one |
| `created_at` | Set by the database |

### `app.refresh_token`

| Column | Notes |
|---|---|
| `id` | UUIDv7 |
| `user_id` | The account; tokens are deleted with it |
| `token_hash` | Unique SHA-256 hash, exactly 32 bytes |
| `family_id` | Shared by every token of one login |
| `expires_at` | The login time plus the refresh TTL, the same for the whole family |
| `revoked_at` | Set on logout or when reuse revokes the family |
| `replaced_by` | Id of the token issued when this one was rotated |
| `created_at` | Set by the application; for a successor, the moment of rotation |

---

## Rate limiting

Public auth endpoints are rate limited with a token bucket per key.

- **Algorithm:** token bucket via Bucket4j. A bucket holds a few tokens, each request takes one, and tokens refill steadily. Unlike a fixed window, there is no double burst at the window edge.
- **Storage:** Redis, through Bucket4j's Lettuce integration. All backend instances share the buckets, the buckets survive redeploys, and each update is atomic in Redis. A key expires once its bucket would be full again.
- **Redis unavailable:** fail open. The request is allowed and a warning is logged. The limiter is a defence layer; a Redis outage must not block sign-ups. The backend connects to Redis on first use, so it also starts while Redis is down. Redis calls time out after 500 ms.
- **Usage:** code calls `RateLimiter.consume(policy, key)`. An empty bucket throws `RateLimitExceededException`.
  - Limits per request are checked in the controller, before any expensive work (BCrypt, database, email). The exception becomes `429 Too Many Requests` with a `Retry-After` header in seconds.
  - The limit on emails per user is checked in `AccountMailer`, so every email goes through it. The mailer catches the exception and skips the email.
- **Configuration:** each policy's limit comes from `app.rate-limit.policies.<policy>` (`capacity` and `period`). The application refuses to start when a policy has no configuration.
- **Scope:** this protects specific endpoints and inboxes from abuse. Protection against floods belongs at the edge proxy.

| Policy | Limit | Key |
|---|---|---|
| `REGISTER` | 3 per minute | Client IP |
| `RESEND_VERIFICATION` | 3 per minute | Client IP |
| `ACCOUNT_MAIL` | 3 per hour | User id |
| `LOGIN` | 5 per minute | Client IP and normalised email |
| `LOGIN_EMAIL` | 20 per hour | Normalised email |
| `SESSION` | 30 per minute | Client IP; shared by refresh and logout |

The login limits slow guessing down and never lock the account: a lockout would let anyone lock a victim out. `LOGIN_EMAIL` stops guessing spread over many IPs.

Redis keys have the form `loc:rate-limit:<policy>:<key>`, for example `loc:rate-limit:register:203.0.113.5`; the `loc:` prefix keeps them apart from other applications on a shared Redis.

### Client IP behind the proxy

In production a request travels:

```text
Browser → Cloudflare edge → cloudflared (host) → 127.0.0.1:3001 → frontend nginx (/api/) → backend (private Docker network)
```

The backend never sees the browser's connection, so `request.getRemoteAddr()` would be the nginx container and every user would share one bucket.

- Cloudflare appends the visitor IP to `X-Forwarded-For`; cloudflared passes it on. The host port is bound to 127.0.0.1, so only the tunnel reaches nginx.
- nginx appends the connecting address to `X-Forwarded-For` (`frontend/nginx.conf.template`). Through the published port that address is the Docker gateway, a private address.
- `server.forward-headers-strategy: native` makes Tomcat read `X-Forwarded-For` from the right, skip trusted private proxies, and expose the real client IP through `getRemoteAddr()`.
- Code never parses `X-Forwarded-For` itself; its left-most entries are whatever the client sent.
- Spring's `ForwardedHeaderFilter` is disabled (`config/OpenApiConfig`). The Scalar API-docs starter would otherwise register it, and it trusts the left-most entry, which would let anyone fake their IP and bypass rate limits. `RegisterRateLimitHttpTest` covers this.
- After any change to the proxy chain, check in production that the resolved IP is the real client IP and that a forged `X-Forwarded-For` header does not change it.

---

## Responses and errors

| Status | When |
|---|---|
| `200` | Logged in, session refreshed, or the current user |
| `202` | Registration or resend accepted, whatever the account state |
| `204` | Email verified, or logged out |
| `400` | Invalid input, malformed JSON, an unknown, expired or used verification token, or an invalid refresh cookie |
| `401` | Wrong email or password, or a missing, invalid or expired access token |
| `403` | Correct password on an unverified account, or an access token without the required role |
| `409` | The refresh cookie was rotated by another request less than 10 seconds ago |
| `429` | Rate limit exceeded, with a `Retry-After` header in seconds |

---

## Configuration

| Variable | Used for | Notes |
|---|---|---|
| `DB_*` / `POSTGRES_*` | Database | Shared with the rest of the backend |
| `REDIS_URL` | Rate limiting | Defaults to `redis://localhost:6379`. In production: the shared Redis with password, e.g. `redis://:<password>@<host>:6379/0` |
| `MAIL_HOST`, `MAIL_PORT` | SMTP server | Default to Mailpit on `localhost:1025` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP login | Empty locally; in production the Resend API key is the password |
| `MAIL_FROM` | Sender address | Defaults to `Loc <no-reply@loc.local>` |
| `FRONTEND_URL` | Base of the links in emails | Defaults to `http://localhost:4200` |
| `JWT_SECRET` | Signing access tokens | Required, Base64 of at least 32 bytes: `openssl rand -base64 32`. Locally in the shell or IDE; in production in `.env.production` |
| `JWT_ACCESS_TOKEN_TTL` | Access-token lifetime | Defaults to `15m` |
| `JWT_REFRESH_TOKEN_TTL` | Session lifetime: how long after login the refresh token stops working | Defaults to `14d` |

