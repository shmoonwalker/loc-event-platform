# Website feedback

`POST /api/feedback` accepts a message from anyone. A signed-in member may send
`Authorization: Bearer <accessToken>`; the member id comes from that token.
Without a token the feedback is stored with no member. A user id in the body is ignored.
An invalid or expired token gets `401`, and so does a token whose account no longer exists.
Interactive docs: `/api/docs` (OpenAPI at `/api/docs/openapi.yaml`).

| Endpoint | Purpose |
| --- | --- |
| `POST /api/feedback` | Send feedback. `201` with the feedback as stored. |
| `GET /api/admin/feedback` | One page of feedback for an admin. Open items first, then newest. |
| `POST /api/admin/feedback/{feedbackId}/handled` | Mark that feedback handled. `200` with the feedback. |

Null fields are left out of the JSON. `userId` is absent for a visitor, `contactEmail` when
none was left, and `handledAt` / `handledBy` until an admin marks it handled.

## Sending

The body is JSON:

- `message` is required. It is stored trimmed and must be 1 to 2000 characters after trimming.
  A missing, blank or longer message gets `400`.
- `contactEmail` is optional. It must be a valid email of at most 254 characters and is stored
  trimmed. A blank value is stored as null. Anything else gets `400`.

Visitors and members share one limit of 3 requests per hour per IP
(`app.rate-limit.policies.feedback`). The IP is `HttpServletRequest.getRemoteAddr()`,
with `server.forward-headers-strategy: native`, the same way registration is limited.
A limited request gets `429` with a `Retry-After` header and is not stored.

## Admin

`/api/admin/feedback` follows the admin rule: a missing or invalid token gets `401`,
a member gets `403`, and an admin is allowed.

`GET` takes `page` (zero-based, default 0) and `size` (1–20, default 20), the same
as `/api/events`. Anything else returns `400`. Open feedback (`handled_at` is null)
comes first. Within open and handled feedback, the newest comes first.

`POST /api/admin/feedback/{feedbackId}/handled` sets `handledAt` and `handledBy` to the
admin of the access token. Marking feedback that is already handled changes nothing and
still answers `200`. An unknown id gets `404`. An id that is not a UUID gets `400`.
