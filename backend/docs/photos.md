# Event photos

A signed-in member can upload photos of a show. A new photo is `PENDING` until an
admin approves it. Pending and rejected photos are never on the public list and
never get a public storage URL. The original upload is not stored.

Interactive docs: `/api/docs` (OpenAPI at `/api/docs/openapi.yaml`).

| Endpoint | Who | Purpose |
| --- | --- | --- |
| `POST /api/events/{eventId}/photos` | member | Upload one photo. `201` with the photo, status `PENDING`. |
| `GET /api/events/{eventId}/photos` | anyone | Approved photos, newest first. |
| `GET /api/me/events/{eventId}/photos` | member | The caller's photos, every status, newest first. |
| `DELETE /api/events/{eventId}/photos/{photoId}` | owner | Delete that photo and its stored files. `204`. |
| `GET /api/events/{eventId}/photos/{photoId}/file?variant=card\|full` | see below | Stream the JPEG. |
| `GET /api/admin/photos?status=PENDING\|APPROVED\|REJECTED` | admin | Up to 50 photos, newest first. Default `PENDING`. |
| `GET /api/admin/photos/{photoId}` | admin | One photo, for the review page. |
| `POST /api/admin/photos/{photoId}/approve` | admin | Mark it `APPROVED`. Returns the photo. |
| `POST /api/admin/photos/{photoId}/reject` | admin | Mark it `REJECTED`. Returns the photo. |
| `DELETE /api/admin/photos/{photoId}` | admin | Remove any photo and its stored files. `204`. |
| `POST /api/events/{eventId}/photos/{photoId}/reports` | member | Report an approved photo. Same body as other reports. |

`{eventId}` may be the show id or an older event id. The stored `eventId` is the show id.

## Photo

```json
{
  "id": "01933d0a-7c8e-7000-8000-000000000001",
  "eventId": "01933d0a-7c8e-7000-8000-000000000002",
  "eventTitle": "Evening at the Bimhuis",
  "status": "PENDING",
  "cardUrl": "/api/events/01933d0a-7c8e-7000-8000-000000000002/photos/01933d0a-7c8e-7000-8000-000000000001/file?variant=card",
  "fullUrl": "/api/events/01933d0a-7c8e-7000-8000-000000000002/photos/01933d0a-7c8e-7000-8000-000000000001/file?variant=full",
  "createdAt": "2026-11-16T21:00:00Z",
  "uploader": { "displayName": "Anouk" }
}
```

`status` is `PENDING`, `APPROVED` or `REJECTED`.

When `EVENT_PHOTO_PUBLIC_URL` is set, an approved photo's `cardUrl` and `fullUrl` are
`{EVENT_PHOTO_PUBLIC_URL}/event-photos/{photoId}/card.jpg` and `.../full.jpg`.
Until that variable is set, and for every pending or rejected photo, those fields
are the file route above. A photo that is not approved never gets a public URL.

## Upload

`multipart/form-data` with one part named `file`. JPEG, PNG or WebP, recognised
from the file content, up to 10 MB. Anything else is `400`. A larger file is `413`.

The file is turned upright, flattened onto white, and re-encoded as JPEG so EXIF,
including GPS, is gone. Two images are stored and the original is not: card,
longest side 640px, and full, longest side 1600px. A picture that is already
smaller is not enlarged. Object keys are `event-photos/{photoId}/card.jpg` and
`event-photos/{photoId}/full.jpg`.

There is no check that the show has already started. An unknown show, or one
whose snapshots are all `UNAVAILABLE`, is `404`. Past, cancelled and postponed
shows stay open. The 11th pending or approved photo for that member and show is
`409`. A rejected photo does not count toward the 10.

Uploads are limited to 10 per member per hour (policy `photo-upload`). A limited
request returns `429` with `Retry-After`. Signed out is `401`.

If no R2 settings exist, the upload answers `503` and nothing is stored.

## Reading and deleting

The public list is approved photos only. The file route streams `image/jpeg`.
`variant` must be `card` or `full`; anything else is `400`. An approved file is
public. A pending or rejected file is `401` when signed out and `403` for any
other member. The owner and an admin can read it.

`DELETE` is the owner only. Someone else's photo is `403`. The stored objects
are deleted. An admin can `DELETE /api/admin/photos/{photoId}` for any photo.
That also resolves open `PHOTO` reports (`resolution` `REMOVED`). The owner's
delete does the same.

An unknown `status` on the admin list is `400`. A non-admin is `403`.

## Reports

`POST /api/events/{eventId}/photos/{photoId}/reports` uses the same body and the
same `report` limiter as [comment reports](reports.md): `reason` required
(`WRONG_INFORMATION`, `SPAM`, `INAPPROPRIATE`, `OTHER`), optional `details` up to
1000 characters. `targetType` is `PHOTO`. Reporting the same photo again while
the first report is open returns that report. A photo that is missing, not on
that show, or not `APPROVED` is `404`. Signed out is `401`.

Open photo reports show up in `GET /api/admin/reports` with the event and comment
reports. `target_type` stays `varchar(16)`.

## Storage

Settings sit beside the avatar ones and share `R2_ENDPOINT` and `R2_REGION`:

| Variable | Purpose |
| --- | --- |
| `EVENT_PHOTO_R2_ACCESS_KEY`, `EVENT_PHOTO_R2_SECRET_KEY` | R2 API token for the photo bucket |
| `EVENT_PHOTO_R2_BUCKET` | Bucket for the card and full JPEGs |
| `EVENT_PHOTO_PUBLIC_URL` | Public domain of that bucket, no trailing slash |

If the event-photo key or bucket is unset, uploads use the avatar bucket and
credentials. `EVENT_PHOTO_PUBLIC_URL` does not fall back: without it, even
approved photos use the file route. With no R2 settings at all, uploads stay off.

`app.event_photo` holds `event_id` (the show id), `uploader_id`, `status` and
`created_at`. Deleting the account removes the rows and the stored files.

## Tests

`EventPhotoHttpTest` runs against real PostgreSQL (Testcontainers, Docker needed)
and a stand-in for R2, so the suite never calls a real bucket. Image, URL and
storage-fallback cases also run without Docker.

```bash
cd backend
./mvnw test -Dtest=EventPhotoHttpTest,EventPhotoImagesTest,EventPhotoUrlsTest,EventPhotoStorageTest checkstyle:check
```
