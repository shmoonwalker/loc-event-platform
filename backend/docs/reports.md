# Reports

A signed-in member can report an event or a comment. The answer is the report
itself. It never includes what an admin later decided. The queue that reads
these reports is a separate piece of work.

Every route needs `Authorization: Bearer <accessToken>`. Without a valid token
the answer is `401`. An admin token answers `403`. The reporter is always the
member of the token, never an id in the body.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/events/{eventId}/reports` | Report an event. `201` with the open report. |
| `POST /api/comments/{commentId}/reports` | Report a comment. `201` with the open report. |

## Body

```json
{ "reason": "SPAM", "details": "Posted on every event." }
```

`reason` is required and one of `WRONG_INFORMATION`, `SPAM`, `INAPPROPRIATE`,
`OTHER`. Anything else is `400`.

`details` is optional, at most 1000 characters. Stored trimmed. Blank text is
stored as null and comes back as `null`.

## Answer

`201` with `id`, `targetType` (`EVENT` or `COMMENT`), `targetId`, `reason`,
`details` and `createdAt`. There is no status and no resolution.

Reporting the same event or comment again, while the first report is still
open, returns that same report and inserts no second row. The answer is still
`201`. A different member can report the same item. Once a report has been
resolved, that member can report the item again.

`targetId` for an event is the show id. `{eventId}` may be that show id or an
older event id from a link made before shows existed. An unknown id (no
`publication.event_snapshot` row) is `404`. An event an admin has hidden is
still reportable when the snapshot exists.

A missing or deleted comment is `404`.

## Limit

Five reports per member per hour (policy `report`). Event reports and comment
reports share that limit. A limited request returns `429` with `Retry-After`,
the number of seconds to wait. A repeat of an open report still counts toward
the limit.

## Storage

`app.report` holds `target_type`, `target_id`, `reporter_id`, `reason`,
`details`, `created_at`, and the admin columns `resolved_at`, `resolved_by`,
`resolution`. The member API never reads or writes those last three.

A partial unique index, `report_open_per_member_idx`, allows one open report
per member per event or comment (`resolved_at IS NULL`). The row is removed
when the account is deleted.

## Tests

`ReportHttpTest` runs against real PostgreSQL (Testcontainers, Docker needed).
The main cases point Redis at `localhost:1`, so the limiter fails open. A
nested case starts Redis with a capacity of two and checks `429`.

```bash
cd backend
./mvnw test -Dtest=ReportHttpTest checkstyle:check
```
