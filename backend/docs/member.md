# Member API

Endpoints for a signed-in member: account security, profile, saved events, followed organizers and comments.
Every route needs `Authorization: Bearer <accessToken>` (see
[Authentication](../../docs/architecture/authentication.md)); without a valid token
the answer is `401`. The member is always the one of the token, never an id in the URL.
The one exception is reading comments, which is public.
Interactive docs: `/api/docs` (OpenAPI at `/api/docs/openapi.yaml`).

| Endpoint | Purpose |
| --- | --- |
| `POST /api/me/password` | Change the password and renew the current browser's refresh cookie. `204`. |
| `POST /api/me/email` | Request a verification link for a new email address. `202`. |
| `DELETE /api/me` | Delete the signed-in account. `204`. |
| `PATCH /api/me/profile` | Change the display name. Returns the account. |
| `PUT /api/me/avatar/preset` | Pick a preset avatar. Returns the account. |
| `POST /api/me/avatar` | Upload a profile picture (`multipart/form-data`, part `file`). Returns the account. |
| `DELETE /api/me/avatar` | Remove the profile picture. Returns the account. |
| `GET /api/me/saved-events` | One page of saved events, most recently saved first. |
| `GET /api/me/saved-events/{eventId}` | Event page of a saved event, also when it is withdrawn or over. |
| `PUT /api/me/saved-events/{eventId}` | Save an event. `204`. |
| `DELETE /api/me/saved-events/{eventId}` | Unsave an event. `204`. |
| `GET /api/me/followed-organizers` | Followed organizers, most recently followed first. |
| `GET /api/me/followed-organizers/events` | One page of upcoming events of followed organizers, soonest first. |
| `PUT /api/me/followed-organizers/{organizerId}` | Follow an organizer. `204`. |
| `DELETE /api/me/followed-organizers/{organizerId}` | Unfollow an organizer. `204`. |
| `GET /api/events/{eventId}/comments` | One page of an event's comments with their replies. Public. |
| `POST /api/events/{eventId}/comments` | Comment on an event. `201` with the comment. |
| `POST /api/comments/{commentId}/replies` | Reply to a top-level comment. `201` with the reply. |
| `PATCH /api/me/comments/{commentId}` | Edit an own comment or reply. `200` with the comment. |
| `DELETE /api/me/comments/{commentId}` | Delete an own comment or reply for everyone. `204`. |

Paged routes take `page` (zero-based, default 0) and `size` (1–20, default 20), the same
as `/api/events`. Anything else returns `400` with a `ProblemDetail` body.

## Account security

All three requests take a JSON body with `currentPassword`. An incorrect password
returns `401`. They share a per-account limit of five attempts per 15 minutes.
A limited request returns `429` with a `Retry-After` header.

- Password changes also take `newPassword`, with the same length limits as registration.
  Other devices lose the ability to refresh. Already issued access tokens expire at
  their normal time. The requesting browser receives a new refresh cookie.
- Email changes also take `newEmail`. The old address remains active until the verification
  link is used. A successful verification sends a notice to the old address. An address
  already in use receives the same empty `202` response.
- Deletion removes the account, refresh, verification and password reset tokens, saved events and follows.
  Existing comments and replies keep their text and show the author as `Deleted user`.
- Deletion also removes the uploaded profile picture from R2.

## Profile and avatar

- The profile routes and `GET /api/auth/me` return the account with `avatarUrl` and
  `avatarPreset`. `avatarUrl` is the uploaded picture when there is one, else the preset.
- `PATCH /api/me/profile` takes `{ "displayName": "..." }` with the same rules as
  registration: required, at most 100 characters, stored trimmed.
- Presets: `SUN`, `MOON`, `LEAF`, `WAVE`, `MOUNTAIN`, `STAR`, `FLOWER`, `CLOUD`. A new
  account gets a random one. Their images ship with the frontend, so the preset
  `avatarUrl` is a path on the frontend origin, such as `/avatars/sun.svg`.
- `PUT /api/me/avatar/preset` takes `{ "preset": "SUN" }`. It also removes an uploaded
  picture, so the chosen preset shows. An unknown preset gets `400`.
- `POST /api/me/avatar` accepts JPEG, PNG or WebP up to 5 MB, recognised by the file's
  content, not its name or content type. The picture is cropped to the centre square,
  turned upright (EXIF orientation), scaled to 512x512 and saved as JPEG. Re-encoding
  drops all metadata, including location and camera data. Transparent areas become
  white. Other files get `400`, larger files `413`.
- Uploads are limited to 5 per hour per member (`429` with `Retry-After`).
- `DELETE /api/me/avatar` removes the picture and shows the preset again. Without a
  picture it changes nothing.

## Saved events

- A member saves a show, not a single date. The id can be a show id or an older event
  id. Saving a show that is already saved through another performance changes nothing;
  unsaving removes every saved performance of the show.
- Saving and unsaving are idempotent: saving a saved event, or unsaving one that is not
  saved, changes nothing and still answers `204`.
- Only an event in `publication.discoverable_events` (published and not over) that an
  admin has not hidden can be saved. An unknown, withdrawn, ended or hidden event gets
  `404`. Unsaving always works, also for withdrawn, ended or hidden events.
- `PUT` answers `401` when the account of the token no longer exists.
- Saved events stay in the list after they are withdrawn or end; they then have
  `available: false`. The card shows the next upcoming date, or the last date when
  none is left.
- A show an admin has hidden is left out of the list and its count, and
  `GET /api/me/saved-events/{eventId}` answers `404` for it. The saved row is kept, so
  the show comes back when it is unhidden.
- `GET /api/me/saved-events/{eventId}` has the same shape as `GET /api/events/{id}`.
  For an unavailable event `occurrences` holds all its dates. `404` when the member did
  not save the event.

```json
{
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1,
  "items": [
    { "event": { "...event card, saved: true..." }, "savedAt": "2030-06-01T10:00:00Z", "available": true }
  ]
}
```

`EventDetail.saveCount` on the public event page counts the members that saved the show.

## Followed organizers

- Following and unfollowing are idempotent and answer `204`. Following an unknown
  organizer gets `404`; unfollowing never does.
- `PUT` answers `401` when the account of the token no longer exists.
- `GET /api/me/followed-organizers` returns `{ id, name, description, site }` per
  organizer, taken from the most recently updated published event of that organizer.
- `GET /api/me/followed-organizers/events` returns the same page and card shape as
  `/api/events`, one card per show. Withdrawn and ended events are left out.
- `GET /api/organizers/{id}` returns `followed` for the signed-in member.

## Comments

- Comments belong to a show, not a single date. The id can be a show id or an older
  event id, like for saved events.
- Anyone can read comments; only signed-in members can write them. With an access token,
  `mine` is `true` on the viewer's own comments.
- Comments stay open on past, cancelled and postponed events. A hidden event (state
  `UNAVAILABLE`) shows no comments and takes none: `404`.
- Top-level comments are newest first, their replies oldest first. Replies cannot be
  replied to: that answers `400`. Replying to a deleted or unknown comment answers `404`.
- Text is plain text, stored trimmed, at most 2000 characters; empty text answers `400`.
- Editing sets `editedAt`. A deleted comment cannot be edited.
- Deleting is for everyone and idempotent. A deleted comment that has replies stays in
  the list with `deleted: true` and no `author` or `body`, so its replies keep their
  place; one without replies, and every deleted reply, is left out. The text of a
  deleted comment is never returned.
- Editing or deleting someone else's comment answers `404`, not `403`, so the API never
  confirms it exists.
- When the account is deleted, the comment stays and `author.displayName` is `Deleted user`.
- `author.avatarUrl` is the same as `avatarUrl` on the account: the uploaded picture, or else the preset. It is `null` for `Deleted user`.
- Posting comments and replies shares one rate limit per member (`comment` policy,
  5 per minute): over it, `429` with `Retry-After`. Editing is not limited.
- Posting answers `401` when the account of the token no longer exists.

```json
{
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1,
  "items": [
    {
      "id": "…", "author": { "displayName": "Anouk", "avatarUrl": "/avatars/sun.svg" }, "body": "Is there parking?",
      "official": false, "deleted": false, "mine": false,
      "createdAt": "2030-06-01T10:00:00Z", "editedAt": null,
      "replies": [ { "id": "…", "author": { "displayName": "Bram", "avatarUrl": "https://images-loc.shadibuilds.dev/avatars/…/….jpg" }, "body": "Yes, behind the venue", "...": "…", "replies": [] } ]
    }
  ]
}
```

## Implementation

```text
user/
  controller/  ProfileController, SavedEventController, FollowedOrganizerController, CommentController
  service/     ProfileService, AvatarImages, AvatarStorage, SavedEventService, FollowedOrganizerService,
               CommentService
  model/       Comment (JPA entity; guards replies, edits and deletes)
  repository/  SavedEventRepository, FollowedOrganizerRepository, CommentQueryRepository (SQL via JdbcClient),
               CommentRepository (JPA)
```

- Both tables live in the backend-owned `app` schema and store the published id
  (`event_id` is a `loc_event_id`, `organizer_id` a `locOrganizerId`) without a foreign
  key into `publication`. Rows are deleted with the account.
- A saved row keeps the `loc_event_id`, which never changes. The show (`loc_group_id`)
  is looked up when reading, because a show id changes when the source changes the
  title. The saved list shows one row per show with the earliest `saved_at`.
- `app.saved_event`: primary key `(user_id, event_id)`, `saved_at`; indexed on
  `(user_id, saved_at DESC)` for the list and on `event_id` for `saveCount`.
- `app.followed_organizer`: primary key `(user_id, organizer_id)`, `followed_at`;
  indexed on `(user_id, followed_at DESC)`.
- Saved events are read from `publication.event_snapshot`, so withdrawn and ended events
  still show. Followed events are read from `publication.discoverable_events`.
- `app.comment` stores the show (`event_id` is a `loc_group_id`), `parent_id` (null for
  top-level comments), `user_id` (set to null when the account is deleted), `body`,
  `official`, `created_at`, `edited_at` and the soft-delete columns `deleted_at`,
  `deleted_by`, `delete_reason`. Deleted rows are kept for moderation and audit.
- Comments are written through the `Comment` entity and read with two queries per page:
  one for the top-level comments, one for all their replies. The SQL itself blanks the
  `body` and `author` of deleted comments.
- Indexed on `(event_id, created_at DESC)` for top-level comments and on
  `(parent_id, created_at)` for replies.

- `app.app_user.avatar_preset` holds the preset, `avatar_key` the R2 object key of the
  uploaded picture (null without one).
- Pictures are stored in a public Cloudflare R2 bucket and loaded by browsers from the
  bucket's own domain (`AVATAR_PUBLIC_URL`), never through the API. Each upload gets a
  new key, `avatars/<userId>/<random>.jpg`, served with a one-year immutable cache.
  The replaced or removed object is deleted after the account is saved; a failed
  delete is logged and leaves an unused object.

## Tests

`SavedEventHttpTest`, `FollowedOrganizerHttpTest` and `CommentHttpTest` run against real
PostgreSQL (Testcontainers, Docker needed). `CommentRateLimitHttpTest`
also starts Redis to check the comment rate limit.

```bash
cd backend
./mvnw clean test checkstyle:check
```
