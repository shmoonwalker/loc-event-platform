# Admin comments

Endpoints for an admin to moderate comments and answer them in public.
Every route needs `Authorization: Bearer <accessToken>` with role `ADMIN`
(see [Authentication](../../docs/architecture/authentication.md)).
A signed-out call answers `401`. A member token answers `403`.
Interactive docs: `/api/docs` (OpenAPI at `/api/docs/openapi.yaml`).

| Endpoint | Purpose |
| --- | --- |
| `DELETE /api/admin/comments/{commentId}` | Remove any comment or reply for everyone. `204`. |
| `POST /api/admin/comments/{commentId}/official-replies` | Reply officially to a top-level comment. `201` with the reply. |

## Removing a comment

- The body is `{ "reason": "..." }`. The reason is required, stored trimmed, and at most
  1000 characters. A missing, blank, or longer reason answers `400`.
- The comment row stays. `deleted_at`, `deleted_by` (the admin) and `delete_reason` record
  the removal. Those columns already exist on `app.comment`; this does not add a migration.
- An unknown comment answers `404`. Removing a comment that is already removed answers
  `204` and leaves who removed it, when, and why as they were.
- Open reports of that comment are resolved in the same step:
  `resolved_at` and `resolved_by` are set and `resolution` becomes `REMOVED`.
  Reports of other comments, event reports, and reports that are already resolved stay
  as they are. This still runs when the comment was already removed, so a report that
  is still open gets closed. `app.report` is created by the reports migration (issue 95);
  this API only updates it.
- The public comment list never returns the text or author of a removed comment.
  A removed top-level comment with replies stays, with `deleted: true` and no `body` or
  `author`. One without replies, and every removed reply, is left out.
- Members still delete only their own comments, through `DELETE /api/me/comments/{commentId}`.
  Someone else's comment answers `404` there, and a member cannot call this admin route.

## Official replies

- The body is the same as a member comment: `{ "body": "..." }`, stored trimmed, 1 to 2000
  characters. Empty or longer text answers `400`.
- The reply is stored with `official: true`. `GET /api/events/{eventId}/comments` returns
  that flag, so the page can show a badge. A member reply stays `official: false`.
- The one-level rule applies. Replying to a reply answers `400` with
  `A reply can't be replied to.` Replying to a missing comment, a removed comment, or a
  comment on a hidden event answers `404`, the same as a member reply.
- An official reply can be removed with the same `DELETE`. Its text then disappears from
  the public list.
- A member cannot post an official reply. The member reply route never sets `official`.

```json
{
  "id": "…",
  "author": { "displayName": "Loc", "avatarUrl": "/avatars/sun.svg" },
  "body": "Yes, the lot behind the venue is open.",
  "official": true,
  "deleted": false,
  "mine": true,
  "createdAt": "2030-06-01T10:00:00Z",
  "editedAt": null,
  "replies": []
}
```

## Implementation

```text
admin/
  AdminCommentController
  AdminCommentService
  RemoveCommentRequest
user/model/Comment   officialReply(...) sets official; reply(...) stays unofficial
```

- Removal and the report update run in one transaction. The report update is SQL in
  `AdminCommentService`; there is no `Report` entity here.
- Official replies are written through `Comment.officialReply`. `official` is set only
  when the row is created.

## Tests

`AdminCommentHttpTest` runs against real PostgreSQL (Testcontainers, Docker needed).
It creates `app.report` from `src/test/resources/db/report.sql`. Redis is pointed at
`redis://localhost:1` so the rate limiter fails open.

```bash
cd backend
./mvnw test -Dtest=AdminCommentHttpTest checkstyle:check
```
