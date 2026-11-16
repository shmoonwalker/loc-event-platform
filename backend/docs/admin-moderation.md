# Admin moderation

Admin-only endpoints for the report queue and for hiding events. Every route needs
`Authorization: Bearer <accessToken>` whose `roles` claim includes `ADMIN`.
A member token answers `403`. A missing, invalid, or expired token answers `401`.
The same `401` is returned when the account of the token no longer exists.
Interactive docs: `/api/docs` (OpenAPI at `/api/docs/openapi.yaml`).

| Endpoint | Purpose |
| --- | --- |
| `GET /api/admin/reports` | Open reports, grouped per reported event or comment. |
| `POST /api/admin/reports/{reportId}/dismiss` | Close one open report without changing the item. `204`. |
| `POST /api/admin/events/{eventId}/hide` | Hide a show. Body `{ "reason": "..." }`. `204`. |
| `DELETE /api/admin/events/{eventId}/hide` | Unhide a show. `204`. |
| `GET /api/admin/hidden-events` | Hidden shows, with their title, who hid them, when, and why. |

## Reports

`GET /api/admin/reports` reads `app.report`. Flyway creates that table in
`V11__report.sql`, which belongs to the member-report work (issue 95). This
branch does not ship that migration. An open report has `resolved_at` null.
There is at most one open report per `(target_type, target_id, reporter_id)`.

Resolved reports are left out. What remains is grouped by `target_type` and
`target_id`, newest activity first:

```json
[
  {
    "targetType": "EVENT",
    "targetId": "11111111-1111-4111-8111-111111111111",
    "openCount": 2,
    "reasons": ["INAPPROPRIATE", "SPAM"],
    "reportIds": [
      "22222222-2222-4222-8222-222222222222",
      "33333333-3333-4333-8333-333333333333"
    ],
    "eventId": "11111111-1111-4111-8111-111111111111",
    "eventTitle": "Jazz in the park"
  },
  {
    "targetType": "COMMENT",
    "targetId": "55555555-5555-4555-8555-555555555555",
    "openCount": 1,
    "reasons": ["SPAM"],
    "reportIds": ["66666666-6666-4666-8666-666666666666"],
    "eventId": "11111111-1111-4111-8111-111111111111",
    "eventTitle": "Jazz in the park",
    "commentBody": "Buy cheap tickets here",
    "commentAuthorName": "Alice"
  }
]
```

- `targetType` is `EVENT` or `COMMENT`. For an event, `targetId` is the show id.
- `reasons` are the distinct reasons, alphabetical:
  `WRONG_INFORMATION`, `SPAM`, `INAPPROPRIATE`, `OTHER`.
- `reportIds` are the open report ids, oldest first. Dismiss one of them to close it.
- `eventId` is the show the item belongs to: the reported show, or the show the
  comment was posted on. An event report filed against an older `loc_event_id`
  still gives the show id. Absent when a reported comment no longer exists.
- `eventTitle` is the title of the show's latest publication snapshot. Absent when
  the show is not in the publication view. Hidden shows keep their title here.
- `commentBody` is the text of a reported comment. Absent for an event and once the
  comment was removed; `eventId` and `eventTitle` stay, so the admin can still open
  the event page.
- `commentAuthorName` is the display name of the comment's author. Absent for an
  event and once the author's account is deleted.
- An empty queue is `[]`.

`POST /api/admin/reports/{reportId}/dismiss` sets `resolution` to `DISMISSED`,
`resolved_by` to the admin, and `resolved_at` to now. The reported event or comment
is not changed. An unknown id, or a report that is already resolved, answers `404`.
An id that is not a UUID answers `400`.

## Hidden events

`app.hidden_event` (Flyway `V12__hidden_event.sql`) stores one row per hidden show:

| Column | Meaning |
| --- | --- |
| `event_id` | Show id (`publication.event_snapshot.loc_group_id`). Primary key. |
| `hidden_by` | Admin who hid it. Cleared when that account is deleted; the show stays hidden. |
| `reason` | Required, trimmed, at most 1000 characters. |
| `hidden_at` | When it was hidden. |

The path id is a show id or an older `loc_event_id`, resolved the same way as the
rest of the API. The row always stores the show id. An unknown id answers `404`.

Hiding a show that is already hidden answers `204` and does not write a second row
or replace the first reason. It still closes any new open `EVENT` reports of that
show. Unhiding a show that is not hidden answers `204`.

Hiding sets `resolution` to `HIDDEN`, `resolved_by` to the admin, and `resolved_at`
to now on open reports whose `target_type` is `EVENT` and whose `target_id` is the
show id. Comment reports, reports of other shows, and reports filed against an
older event id are left open. Unhiding does not reopen reports.

The publication row is never updated. Admin does not edit catalog facts.

`GET /api/admin/hidden-events` is most recently hidden first:

```json
[
  {
    "eventId": "11111111-1111-4111-8111-111111111111",
    "hiddenBy": "44444444-4444-4444-8444-444444444444",
    "hiddenByName": "Ada",
    "hiddenAt": "2026-11-16T20:00:00Z",
    "reason": "Duplicate listing",
    "eventTitle": "Jazz in the park"
  }
]
```

`eventTitle` is the title of the show's latest publication snapshot. The public
`GET /api/events/{id}` answers `404` for a hidden show, so this list is where an
admin sees the title. It is absent when the show is no longer in the publication view.

A missing, blank, or over-long reason answers `400`. The reason is stored trimmed.

## Where a hidden show disappears

Public reads add this filter, and do not change the publication view:

```sql
NOT EXISTS (SELECT 1 FROM app.hidden_event h WHERE h.event_id = s.loc_group_id)
```

A hidden show is left out of:

- `GET /api/home` (every rail)
- `GET /api/events` (browse and search, including city suggestions that count shows)
- `GET /api/organizers/{id}` (its events; the organizer answers `404` when every
  discoverable show of theirs is hidden)
- `GET /api/me/followed-organizers/events`
- `GET /api/events/{id}`, which answers `404` for the show id and for an older event id

There is no similar-events query yet (the event page says "Coming soon"). A later
one has to use the same `NOT EXISTS` filter.

A member who saved the show still sees it on `GET /api/me/saved-events` and
`GET /api/me/saved-events/{eventId}`. Unhiding brings the show back on the public
surfaces above.

## Tests

`AdminReportHttpTest` and `HiddenEventHttpTest` create `app.report` themselves from
`src/test/resources/db/report.sql` (`CREATE TABLE IF NOT EXISTS`). That script is
not a Flyway migration.
