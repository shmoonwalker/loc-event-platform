# Discovery API: product behavior and frontend integration

This document describes the implemented discovery contract. The homepage helps
people find something useful immediately; the events collection supports ordinary
browsing, search, and precise filters. These are different experiences sharing the
same repository. No frontend implementation is included in this change.

## Endpoints

All four endpoints permit anonymous GET requests:

| Endpoint | Responsibility |
| --- | --- |
| `/api/home?city=amsterdam` | Four discovery previews and navigation links |
| `/api/events` | Search/filter/sort/page logical events; every input is optional |
| `/api/events/filter-options` | Complete supported filter vocabulary and limits, without counts |
| `/api/cities?q=amster` | Up to ten city suggestions from upcoming published events |

`/api/docs` serves interactive documentation. The configured OpenAPI endpoint is
`/api/docs/openapi.yaml`. A separate event-detail endpoint is not implemented yet.

## Homepage

The default near-you city is Amsterdam. `city` changes **only Near you**. Tonight
and Weekend cover physical events in all cities. Online has no city restriction.
The frontend should locate the city selector beside Near you so its scope is clear.
Remembering the selected city is a frontend concern: send it with each request.
No GPS or radius filtering is used.

| Response field | Mode | Matching events |
| --- | --- | --- |
| `nearYou` | `NEAR_YOU` | Physical, selected city, starting in the next 30 calendar days |
| `tonight` | `TONIGHT` | Physical, starting today from 18:00 inclusive until midnight exclusive, not ended |
| `tonight` when empty | `STARTING_SOON` | Earliest upcoming physical events across all cities, with no 30-day cutoff |
| `thisWeekend` | `WEEKEND` | Physical, starting Saturday 00:00 through Monday 00:00 exclusive, not ended |
| `online` | `ONLINE` | Online, starting in the next 30 calendar days |

All calendar boundaries use **Europe/Amsterdam**, including daylight-saving time.
Saturday/Sunday use the current weekend. Monday-Friday use the coming weekend.
Tonight does not mean the entire day, and after-midnight events belong to the next
date. An event that started during the evening and has not ended may remain in
Tonight. Near-you, online, ordinary browse, and the fallback select upcoming starts.

### Empty and sparse results

Only the homepage Tonight section has an automatic fallback:

1. Search Tonight.
2. If the total is zero, search upcoming physical events, sorted by start time.
3. Return up to six real events with `mode=STARTING_SOON`.
4. Render the heading **Starting soon**, never Tonight, for that mode.

This returns at least three when three are available, but does not manufacture
results if only zero, one, or two exist. A nonempty Tonight section is not padded
with events from other dates. Even events beyond 30 days can appear in the fallback.
The fallback excludes online, expired, withdrawn, and ended events.

Near you never substitutes another city. Weekend never substitutes weekday events.
They return `total=0`, `items=[]`, and `hasMore=false` when empty. Render an honest
empty message (or omit an empty Weekend section), with a broader browsing action.
An unknown but well-formed city slug remains selected and has an empty result; its
fallback display label is derived from the slug.

**Explicit `/api/events` requests never fall back.** An empty search remains empty.

### Section response and navigation

A section contains:

| Field | Meaning |
| --- | --- |
| `mode` | Stable meaning used by the frontend to choose the heading |
| `total` | Full number of matching distinct events, not only preview cards |
| `items` | Up to six cards, ordered by start time then event ID |
| `hasMore` | `total > items.length` |
| `browseUrl` | Relative API URL reproducing this section's effective criteria, page zero |
| `broaderBrowseUrl` | Relative API URL preserving place but removing city and date restrictions |

The root `browseUrl` links to ordinary browsing across all places and cities.
The root `nearYouCity` contains `slug` and `name`. The root has no category counts
or tag chips. Filter controls are populated independently.

An illustrative empty near-you section:

```json
{
  "mode": "NEAR_YOU",
  "total": 0,
  "items": [],
  "hasMore": false,
  "browseUrl": "/api/events?city=amsterdam&when=upcoming&place=physical&sort=start_time&page=0&size=20",
  "broaderBrowseUrl": "/api/events?place=physical&sort=start_time&page=0&size=20"
}
```

Exact **See all** navigation uses `browseUrl` and starts at page zero: the six
preview cards are not a separate pagination page. **Browse all physical events**
or **Browse all online events** uses `broaderBrowseUrl`, labelled to communicate
that restrictions are being removed. This is available even if `hasMore=false`:
there may be online events beyond the section's 30-day preview window.

These URLs are API links, not browser UI routes. Angular can translate their query
parameters into its `/events` route and call the API with those parameters. The
backend does not assume a frontend hostname or route hierarchy.

### Sparse-catalog example

If nine upcoming physical events are outside Amsterdam and after this weekend,
plus six online events within 30 days and five online events later:

- Near you remains Amsterdam and is empty.
- Tonight changes to Starting soon and previews six of the nine physical events.
- Weekend stays empty.
- Online previews six; its exact collection has six and its broader collection has eleven.
- Root Browse all returns all twenty distinct events.
- No unrelated 18-month category counts or physical-only tag chips appear beside online cards.

## Event cards

Cards expose exactly these presentation fields:

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

`place` is the uppercase response enum `PHYSICAL` or `ONLINE`; query values are
lowercase. Use this field rather than inferring online status from null city data.
Dates are UTC instants; render them using the intended timezone. If publication
omits an end, `endAt` falls back to `startAt` without inventing a duration.

Online cards may have null city, venue, and image. Render Online, omit absent venue
information, and show an image placeholder. Categories, tags, description,
organizers, source URLs, internal occurrence IDs, and publication bookkeeping are
not card fields. Filtering still uses categories/tags internally. Rich event detail
is a separate future endpoint, not silently included here.

One card represents one logical `loc_event_id`. Date/time and other filters are
applied to occurrences first; the earliest matching occurrence supplies the card.
Equal starts use occurrence ID as a deterministic selection tie-break, then cards
are ordered by start time and event ID (or relevance first).

## Ordinary browsing and search

All of these work through the same endpoint:

- No text and no filters: `/api/events`
- Text only: `/api/events?q=jazz`
- Filters only: `/api/events?city=amsterdam&place=physical`
- Text plus filters: `/api/events?q=jazz&city=amsterdam&tag=free`

No city is implicitly applied to the ordinary collection. No `when` means upcoming
starts with no upper date horizon. Search uses PostgreSQL full-text search over
title, description, venue, city, category names, and tags. It is not fuzzy spelling
correction or semantic/vector search.

### Query parameters

| Parameter | Meaning / default |
| --- | --- |
| `q` | Optional trimmed text, at most 100 characters; blank means absent |
| `city` | Selected city slug; optional; case normalized |
| `category` | Repeat for OR: `category=music-nightlife&category=arts-culture` |
| `tag` | Repeat for AND: `tag=free&tag=outdoor` |
| `place` | `physical` or `online`; omitted includes both |
| `when` | `tonight`, `weekend`, `upcoming` (next 30 days); optional |
| `dateFrom`, `dateTo` | Paired inclusive local start dates, `YYYY-MM-DD`, years 0001-9999 |
| `timeFrom`, `timeTo` | Paired local start times, `HH:mm`; inclusive start and exclusive end |
| `sort` | `start_time` or `relevance`; defaults to relevance with q, otherwise start time |
| `page` | Nonnegative 32-bit index, starting at zero; default 0 |
| `size` | 1-20; default 20 |

Across groups filters combine with AND. Categories combine with OR within their
group; tags combine with AND within theirs. Duplicate selections are normalized.
Relevance without text falls back to chronological ordering.

Selecting `place=online` with a city returns 400: the frontend should clear/disable
the city when selecting Online, rather than silently ignoring an active filter.

### Dates and time of day

Example: all three selected dates, 18:00-23:00 on each date:

```http
GET /api/events?dateFrom=2030-06-08&dateTo=2030-06-10&timeFrom=18:00&timeTo=23:00
```

Custom dates and a `when` preset cannot be combined. Time-of-day filtering can be
used alone, with custom dates, or to narrow a preset. Custom dates can include
still-running events that started earlier, but ended events remain excluded.

The backend converts inclusive dateTo to an exclusive next-midnight instant. Date
windows can therefore contain 23 or 25 hours during DST transitions. Daily start
time filtering is performed in PostgreSQL using `AT TIME ZONE 'Europe/Amsterdam'`.

An overnight range, e.g. `22:00` to `02:00`, matches start times >=22:00 OR <02:00.
**The date filter always refers to the occurrence's own local start date.** Selecting
only June 8 includes June 8 at 01:00 and 23:00, not June 9 at 01:00. Select both dates
for an evening-and-next-morning visit. Equal times are invalid; omit both for all day.
During the autumn repeated hour, both matching real instants qualify. A nonexistent
spring clock time has no matching occurrence; no timestamp is invented.

### Validation and errors

Invalid input returns HTTP 400 with `ProblemDetail`. Cases include unknown enum,
category or tag values; malformed dates/times; missing range endpoints; reversed
dates; equal times; preset/date conflicts; city combined with Online; oversized
search strings; invalid page/size types or bounds. Valid filters with no matches
return HTTP 200 and an empty page, not an error or fallback.

### Pagination

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3,
  "items": []
}
```

`items` above is abbreviated. Page zero shows UI page 1. Next uses `page+1` and
preserves every active filter, text, sort, and size. Reset page to zero whenever
those criteria change. Previous is enabled when `page>0`; Next when
`page+1<totalPages`. Beyond-last requests return an empty items list with actual totals.

The old page-50 ceiling is removed. SQL offsets use long arithmetic to avoid integer
overflow. Offset pagination remains potentially expensive at large offsets and can
shift under concurrent publication changes; deterministic ordering does not give
snapshot consistency across separate HTTP requests. Cursor pagination remains a
possible future optimization, not part of this contract.

## Complete filter options and city suggestions

`/api/events/filter-options` returns `categories`, `tags`, `sorts`, `datePresets`,
`places`, `timezone`, `defaultPageSize`, `maxPageSize`, and `maxQueryLength`.
Each option has `value` (query parameter value) and `label` (display text).
Categories/tags come from the supported enum catalogs, including options without
current events. They are not restricted to eight tags or the homepage's physical
results. There are no counts and no database query for this vocabulary endpoint.
The UI may localize labels; filter values remain stable.

City suggestions use `/api/cities?q=Den%20Haag`. Display the returned name and send
the returned slug, e.g. `city=den-haag`. With no q, Amsterdam appears first if
available. Suggestions include cities with upcoming published events, not a complete
geographic directory. Search q is limited to 100 characters. SQL wildcard characters
are removed from city-search text. Different names such as Den Bosch and
's-Hertogenbosch are not yet merged into a canonical municipality.

## Angular integration expectations

1. Load `/api/home` with the saved near-you city, or omit city for Amsterdam.
2. Render each section according to mode; handle null presentation fields.
3. Keep a prominent Browse all action using the root link.
4. Open exact or broader collection links with their corresponding labels.
5. Load filter-options independently; load city suggestions as the user types.
6. Store filter/page/sort/search state in the browser URL for refresh and Back/Forward.
7. Debounce text/city typing and cancel obsolete requests to prevent stale results.
8. Show loading, failure, and empty states separately; never replace an explicit
   empty filtered result with unrelated events.
9. Display each start's date and time. Mixed city collections need city/venue on cards.
10. Use a same-origin reverse proxy or a development proxy for `/api`; separate-origin
    deployments need a deliberate CORS policy. This backend change adds neither a
    frontend nor an unrestricted CORS policy.

## Compatibility and deployment

This deliberately changes the earlier, not-yet-integrated discovery contract:

- Home no longer returns `tags` or `categories` and has a root `browseUrl`.
- Cards no longer return `categories` or `tags`; they add explicit `place`.
- Sections add `mode`, `browseUrl`, and `broaderBrowseUrl`.
- Tonight now means evening and has homepage-only fallback.
- City suggestions follow upcoming starts without the old 18-month facet horizon.
- Filter options and custom ranges are new; page indexes above 50 are accepted.

Clients must update their types/field access and labels. Rebuild/restart the backend
process or image to serve this contract; editing source does not update an existing
Docker container. No publication-schema migration is required. Never rebuild an
unrelated frontend as part of this backend change.

## Regression coverage

The HTTP tests use a real Testcontainers PostgreSQL and a fixed application clock.
They cover normal/sparse/empty catalogs, fallback counts of 0/1/2/3/6/7, no padding,
exact link equivalence, broader links, unknown cities, online nulls, complete filter
options, combined search/filters, inclusive dates, exclusive daily times, overnight
ranges, repeated DST hours, invalid inputs, large offsets, event grouping, and
internal-field exclusion. TimeWindow unit tests cover evening boundaries, weekend
rollover, and 23/25-hour custom dates. Publication expiry is controlled with fixture
validUntil values separately from the fixed Java clock because the view uses DB time.
