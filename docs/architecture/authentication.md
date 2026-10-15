# Authentication

How users register with Loc, how they prove they own their email address, how passwords and tokens are stored, and how registration is protected against abuse.

---

## Principles

- **No account enumeration.** Registration returns the same response and follows the same password-hashing path whether an email is new or already registered. Only the owner of the inbox learns what happened, by email.
- **Spring Security first.** Passwords are encoded with Spring Security's password encoder.
- **Secrets are stored only as hashes.** Passwords use BCrypt; email verification tokens use SHA-256.
- **The public registration endpoint is rate limited.**
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
4. What happens next depends on the email:

   | Email | Account | Email sent |
   |---|---|---|
   | New | Created unverified, with role `USER` | Verification link |
   | Registered and verified | Not changed | "Someone tried to sign up with your email", with a link to password reset |
   | Registered but unverified | Password and display name replaced by the new ones | A fresh verification link |

5. The response is always `202 Accepted` with an empty body: no id and no account details, so the response never reveals which emails have an account.

Two simultaneous registrations for the same email are resolved by the database: the unique constraint `app_user_email_key` rejects the second insert, and that violation is treated like an existing email; the losing request sends no email. Any other constraint violation is a bug and surfaces as an error.

An unverified account takes the password of its latest registration. Otherwise someone could register another person's address first, wait for the owner to register and verify, and then sign in with the password they chose.

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
| `user/service/RegistrationService` | Normalises, hashes, handles the three cases above and triggers the email |
| `user/service/EmailVerificationService` | Issues verification tokens and verifies them |
| `user/service/SecureTokens` | Generates random tokens and hashes them with SHA-256 |
| `user/mail/AccountMailer` | Sends the account emails asynchronously |
| `user/repository/UserAccountRepository` | Spring Data JPA repository for `app_user` |
| `user/repository/EmailVerificationTokenRepository` | Spring Data JPA repository for `email_verification_token` |
| `user/model/UserAccount`, `user/model/EmailVerificationToken` | JPA entities |

---

## Email verification

A new account is unverified until its owner opens the link from the verification email.

- **Token:** 32 random bytes from `SecureRandom`, encoded as URL-safe Base64 without padding (43 characters). Only its SHA-256 hash is stored. SHA-256 is enough because the token is random rather than chosen by a person, and an unsalted hash lets the token be looked up directly.
- **Lifetime:** 24 hours. Issuing a new token marks the user's older unused tokens as used, so only the latest link works.
- **Link:** `FRONTEND_URL/verify-email?token=…`. It opens a frontend page instead of an API endpoint, because mail security scanners open links in emails and a plain `GET` would use up the token.
- **Verifying:** the token row is locked while it is checked, so two simultaneous requests cannot both use it. Unknown, expired and used tokens are rejected with the same error.

### Sending email

- Email is sent asynchronously (`@Async`), after the database changes are committed. A slow or failing mail server neither delays registration nor changes its response.
- `RegistrationService` is deliberately not `@Transactional`: each database step commits on its own before the email is handed to the background thread.
- Plain-text emails through Spring's `JavaMailSender`. SMTP calls time out after 5 seconds.
- Logs name the user id only, never the email address, the token or the link.
- Local development uses [Mailpit](https://mailpit.axllent.org) from `docker-compose.yml`. It accepts all email and shows it at `http://localhost:8025`; nothing reaches a real inbox.
- The `prod` profile requires SMTP authentication and STARTTLS, and refuses to send unencrypted.

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
| `user_id` | References `app_user`; tokens are deleted with their user |
| `token_hash` | SHA-256 of the token, unique; a CHECK requires exactly 32 bytes |
| `expires_at` | 24 hours after `created_at` |
| `used_at` | Set when the token is used or replaced by a newer one |
| `created_at` | Set by the application from the injected `Clock` |

---

## Rate limiting

Public auth endpoints are rate limited with a token bucket per key.

- **Algorithm:** token bucket via Bucket4j. A bucket holds a few tokens, each request takes one, and tokens refill steadily. Unlike a fixed window, there is no double burst at the window edge.
- **Storage:** Redis, through Bucket4j's Lettuce integration. All backend instances share the buckets, the buckets survive redeploys, and each update is atomic in Redis. A key expires once its bucket would be full again.
- **Redis unavailable:** fail open. The request is allowed and a warning is logged. The limiter is a defence layer; a Redis outage must not block sign-ups. The backend connects to Redis on first use, so it also starts while Redis is down. Redis calls time out after 500 ms.
- **Usage:** controllers call `RateLimiter.consume(policy, key)` before any expensive work (BCrypt, database, email). An empty bucket throws `RateLimitExceededException`, which becomes `429 Too Many Requests` with a `Retry-After` header in seconds.
- **Configuration:** each policy's limit comes from `app.rate-limit.policies.<policy>` (`capacity` and `period`). The application refuses to start when a policy has no configuration.
- **Scope:** this protects specific endpoints from abuse. Protection against floods belongs at the edge proxy.

| Policy | Limit | Key |
|---|---|---|
| `REGISTER` | 3 per minute | Client IP |

Redis keys have the form `rate-limit:<policy>:<key>`, for example `rate-limit:register:203.0.113.5`.

### Client IP behind the proxy

In production a request travels:

```text
Browser → Railway edge proxy → frontend nginx (/api/) → backend (Railway private network)
```

The backend never sees the browser's connection, so `request.getRemoteAddr()` would be the nginx container and every user would share one bucket.

- nginx appends the connecting address to `X-Forwarded-For` (`frontend/nginx.conf.template`).
- `server.forward-headers-strategy: native` makes Tomcat read `X-Forwarded-For` from the right, skip trusted private proxies, and expose the real client IP through `getRemoteAddr()`.
- Code never parses `X-Forwarded-For` itself; its left-most entries are whatever the client sent.
- Spring's `ForwardedHeaderFilter` is disabled (`config/OpenApiConfig`). The Scalar API-docs starter would otherwise register it, and it trusts the left-most entry, which would let anyone fake their IP and bypass rate limits. `RegisterRateLimitHttpTest` covers this.
- After any change to the proxy chain, check in production that the resolved IP is the real client IP and that a forged `X-Forwarded-For` header does not change it.

---

## Responses and errors

| Status | When |
|---|---|
| `202` | Registration accepted, whatever the account state |
| `400` | Invalid input or malformed JSON |
| `429` | Rate limit exceeded, with a `Retry-After` header in seconds |

---

## Configuration

| Variable | Used for | Notes |
|---|---|---|
| `DB_*` / `POSTGRES_*` | Database | Shared with the rest of the backend |
| `REDIS_URL` | Rate limiting | Defaults to `redis://localhost:6379`. In production: Railway Redis on the private network, with password |
| `MAIL_HOST`, `MAIL_PORT` | SMTP server | Default to `localhost` and `1025` (Mailpit) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP login | Empty by default; Mailpit needs none |
| `MAIL_FROM` | Sender of account emails | Defaults to `Loc <no-reply@loc.local>` |
| `FRONTEND_URL` | Base of links in emails | Defaults to `http://localhost:4200` |

---

## Planned design

- `POST /api/auth/verify-email` accepts the token from the link, marks the token used and the account verified, and returns the same error for unknown, expired and used tokens. Verification uses `EmailVerificationService.verify`.
- `POST /api/auth/resend-verification` accepts an email and always returns the same response; only an unverified account gets a fresh link.
- Rate limits: `resend-verification` 3 per minute per client IP, and at most 3 emails per hour to one address, whatever triggers them.
