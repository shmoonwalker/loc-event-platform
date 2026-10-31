# Member API

Endpoints for a signed-in member: saved events and followed organizers.
Every route needs `Authorization: Bearer <accessToken>` (see
[Authentication](../../docs/architecture/authentication.md)); without a valid token
the answer is `401`. The member is always the one of the token, never an id in the URL.
Interactive docs: `/api/docs` (OpenAPI at `/api/docs/openapi.yaml`).

| Endpoint | Purpose |
| --- | --- |
| `GET /api/me/saved-events` | One page of saved events, most recently saved first. |
| `GET /api/me/saved-events/{eventId}` | Event page of a saved event, also when it is withdrawn or over. |
| `PUT /api/me/saved-events/{eventId}` | Save an event. `204`. |
| `DELETE /api/me/saved-events/{eventId}` | Unsave an event. `204`. |
| `GET /api/me/followed-organizers` | Followed organizers, most recently followed first. |
| `GET /api/me/followed-organizers/events` | One page of upcoming events of followed organizers, soonest first. |
| `PUT /api/me/followed-organizers/{organizerId}` | Follow an organizer. `204`. |
| `DELETE /api/me/followed-organizers/{organizerId}` | Unfollow an organizer. `204`. |

Paged routes take `page` (zero-based, default 0) and `size` (1–20, default 20), the same
as `/api/events`. Anything else returns `400` with a `ProblemDetail` body.

## Saved events

- A member saves a show, not a single date. The id can be a show id or an older event
  id. Saving a show that is already saved through another performance changes nothing;
  unsaving removes every saved performance of the show.
- Saving and unsaving are idempotent: saving a saved event, or unsaving one that is not
  saved, changes nothing and still answers `204`.
- Only an event in `publication.discoverable_events` (published and not over) can be
  saved. An unknown, withdrawn or ended event gets `404`. Unsaving always works, also for withdrawn or ended events.
- `PUT` answers `401` when the account of the token no longer exists.
- Saved events stay in the list after they are withdrawn or end; they then have
  `available: false`. The card shows the next upcoming date, or the last date when
  none is left.
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

## Implementation

```text
user/
  controller/  SavedEventController, FollowedOrganizerController
  service/     SavedEventService, FollowedOrganizerService
  repository/  SavedEventRepository, FollowedOrganizerRepository (SQL via JdbcClient)
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

## Tests

`SavedEventHttpTest` and `FollowedOrganizerHttpTest` run against real PostgreSQL
(Testcontainers, Docker needed) with a fixed clock.

```bash
cd backend
./mvnw clean test checkstyle:check
```
