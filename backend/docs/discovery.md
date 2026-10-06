# Discovery API

Public, read-only endpoints for finding events. Anonymous `GET` only.
Interactive docs: `/api/docs` (OpenAPI at `/api/docs/openapi.yaml`).

| Endpoint | Purpose |
| --- | --- |
| `GET /api/events` | The event list: search, filter, sort, page. Every list in the app uses it. |
| `GET /api/home?city=amsterdam` | Homepage highlight rails. Only rails with events are returned. |
| `GET /api/events/filter-options` | Values and labels for the filter controls. |
| `GET /api/cities?q=amster` | Up to ten city suggestions for the city picker. |

| `GET /api/events/{id}` | Event page: description, address, coordinates, categories, tags, images, organizers, every upcoming date, forecast (`weather`, null when none). `404` when unknown or over. |
| `GET /api/organizers/{id}` | Organizer profile (`name`, `description`, `site`) plus up to 20 upcoming event cards. Organizer ids come from `EventDetail.organizers[].id`. `404` when none. |
| `GET /api/categories`, `GET /api/tags` | `{ value, label }` lists, same as in filter-options. |

Online vs in person is the `place` filter (`physical` / `online`) on `/api/events`; every card and detail carries `place`.

## Homepage: how the frontend uses it

1. Call `GET /api/home` and `GET /api/events` in parallel.
2. Render the rails (zero to four), then the event list below them.
3. Search, filters, sort, and paging call only `/api/events`. Keep that state in the
   browser URL so refresh and Back work. Reset `page` to 0 when criteria change.
4. "See all" on a rail opens the list with that rail's `filters`.

The list is never empty while upcoming events exist, so the homepage never looks dead,
even when every rail is missing.

## `GET /api/home`

```json
{
  "nearYouCity": { "slug": "amsterdam", "name": "Amsterdam" },
  "rails": [
    {
      "mode": "TONIGHT",
      "total": 3,
      "items": [ "...up to 6 event cards..." ],
      "hasMore": false,
      "filters": { "when": "tonight", "place": "physical" }
    }
  ]
}
```

| Rail `mode` | Events | `filters` |
| --- | --- | --- |
| `TONIGHT` | Physical, starting today 18:00 until midnight, not ended | `when=tonight&place=physical` |
| `WEEKEND` | Physical, starting Saturday 00:00 until Monday 00:00 | `when=weekend&place=physical` |
| `NEAR_YOU` | Physical, in `city`, upcoming | `city=…&when=upcoming&place=physical` |
| `ONLINE` | Online, upcoming | `when=upcoming&place=online` |

- A rail with zero events is left out. A rail with one event is shown with one card.
  No rail is padded or replaced with other events. `rails` can be `[]`.
- Rails always come in the order above. `hasMore` is `total > items.length`.
- `city` (a slug, default `amsterdam`) changes only the Near you rail. `nearYouCity` is
  returned even when that rail is missing, so the city picker can stay visible.
  An unknown but valid slug stays selected; its name is derived from the slug.
  No GPS or radius search.

## `GET /api/events`

With no parameters: every upcoming event, soonest first, page 0, 20 per page.

| Parameter | Meaning |
| --- | --- |
| `q` | Full-text search on title, description, venue, city, categories, tags. Max 100 chars. |
| `city` | City slug, e.g. `amsterdam`. Not allowed with `place=online`. |
| `category` | Repeat for OR: `category=music-nightlife&category=arts-culture` |
| `tag` | Repeat for AND: `tag=free&tag=outdoor` |
| `place` | `physical` or `online`. Omit for both. |
| `when` | `tonight`, `weekend`, or `upcoming` (every event not yet ended). |
| `dateFrom`, `dateTo` | Both required together. Inclusive local dates `YYYY-MM-DD`. Not allowed with `when`. |
| `timeFrom`, `timeTo` | Both required together. Local start time `HH:mm`, end exclusive. `22:00`–`02:00` is overnight. |
| `sort` | `start_time` (default) or `relevance` (default when `q` is set). |
| `page` | Zero-based, default 0. |
| `size` | 1–20, default 20. |

```json
{ "page": 0, "size": 20, "totalElements": 45, "totalPages": 3, "items": [ "...event cards..." ] }
```

- Filters from different groups combine with AND.
- Ended events are never returned. A filter that matches nothing returns `200` with
  an empty list; it never falls back to other events. Show an empty state.
- Invalid input returns `400` with a `ProblemDetail` body.
- One card per event. If an event has several dates, the earliest date that matches
  the filters is shown.
- All calendar rules use Europe/Amsterdam time, including daylight saving.
  A date filter matches the event's own local start date.

### Event card

Rails and the list return the same card, so the frontend needs one card component.

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "title": "Jazz evening",
  "startAt": "2030-06-08T18:00:00Z",
  "endAt": "2030-06-08T21:00:00Z",
  "place": "PHYSICAL",
  "citySlug": "amsterdam",
  "cityName": "Amsterdam",
  "venueName": "Paradiso",
  "imageUrl": null
}
```

Times are UTC. `place` is `PHYSICAL` or `ONLINE`. Online cards usually have null
city, venue, and image: show "Online" and an image placeholder.

## Filter options and cities

`/api/events/filter-options` returns `categories`, `tags`, `sorts`, `datePresets`,
`places`, `timezone`, and the page/query limits. Each option is `{ value, label }`:
send `value`, show `label`. No counts, no database query.

`/api/cities?q=den` returns `{ slug, name }` for cities with upcoming events.
Show `name`, send `slug`. With no `q`, Amsterdam comes first.

## Implementation

```text
event/
  controller/  PublicBrowseController (HTTP), BrowseCriteriaParser (validation → BrowseCriteria)
  service/     EventBrowseService (time windows, sort), HomeService (rails), FilterOptionsService
  repository/  PublicEventRepository (SQL via JdbcClient), EventCardRowMapper
  model/       BrowseCriteria, TimeWindow (Amsterdam calendar), When, Place, EventSort, RailMode
  dto/         request query records, response records
```

- Data comes from the `publication.discoverable_events` view (published, not expired
  snapshots written by the data application). The backend never writes to it.
- The repository counts distinct events, skips the card query when the count is 0,
  and uses `DISTINCT ON (loc_event_id)` to pick one occurrence per event.
  User input is always bound as parameters, never concatenated into SQL.
- A rail is just `/api/events` criteria with page size 6. The homepage makes up to
  nine SQL reads (city, plus count and cards per non-empty rail). No cache.

## Tests

`PublicEventsHttpTest` runs against real PostgreSQL (Testcontainers, Docker needed)
with a fixed clock. `TimeWindowTest` covers the Amsterdam time windows.

```bash
cd backend
./mvnw clean test checkstyle:check
```
