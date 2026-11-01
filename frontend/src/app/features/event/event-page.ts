import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, linkedSignal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { DomSanitizer, Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthSession } from '../../core/auth-session';
import { categoryImage } from '../../core/event-image';
import { EventsApi, QueryParams } from '../../core/events-api';
import { externalUrl } from '../../core/external-url';
import { EMPTY_FILTERS, Filters, filtersToParams } from '../../core/filters';
import { errorStatus, valueOf } from '../../core/resource';
import { SavedEventsApi } from '../../core/saved-events-api';
import { amsterdamDate, formatWhen } from '../../core/time';
import { ErrorState } from '../../shared/error-state/error-state';
import { IconComponent } from '../../shared/icon/icon';
import { NotFoundState } from '../../shared/not-found-state/not-found-state';
import { weatherMessage, weatherView } from './weather';
import { SaveButton } from '../../shared/save-button/save-button';

/** One event: everything the API knows about it. 404 (unknown or already over) gets a calm not-found state. */
@Component({
  selector: 'app-event-page',
  imports: [RouterLink, IconComponent, ErrorState, NotFoundState, SaveButton],
  templateUrl: './event-page.html',
})
export class EventPage {
  private readonly api = inject(EventsApi);
  private readonly savedApi = inject(SavedEventsApi);
  private readonly title = inject(Title);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly session = inject(AuthSession);

  /** The :id route parameter. Changing it (a link to another event) refetches. */
  readonly id = input.required<string>();

  protected readonly detail = rxResource({
    params: () => this.id(),
    stream: ({ params }) =>
      this.api.event(params).pipe(
        // Withdrawn or past events are gone from the public API; the member who saved one can still open it.
        catchError((err: HttpErrorResponse) =>
          err.status === 404 && this.session.loggedIn()
            ? this.savedApi.get(params)
            : throwError(() => err),
        ),
      ),
  });

  protected readonly event = computed(() => valueOf(this.detail));
  protected readonly notFound = computed(() => errorStatus(this.detail) === 404);
  protected readonly online = computed(() => this.event()?.place === 'ONLINE');
  /** The main image, else the same category photo the event card falls back to (decorative, so no alt text). */
  /** Set when the listing's image fails to load (dead link, hotlink block); resets for the next event. */
  protected readonly imageFailed = linkedSignal({ source: this.event, computation: () => false });
  protected readonly image = computed(() => {
    const e = this.event();
    const own = this.imageFailed() ? undefined : e?.imageUrls[0]?.trim();
    return {
      src: own || categoryImage(e?.categories[0]?.value),
      alt: own ? (e?.title ?? '') : '',
    };
  });
  /** Index of the picked date in occurrences; back to the first date when another event opens. */
  protected readonly picked = linkedSignal({ source: this.event, computation: () => 0 });
  protected readonly occurrence = computed(() => this.event()?.occurrences[this.picked()]);
  protected readonly dates = computed(
    () => this.event()?.occurrences.map((o) => formatWhen(o.startAt, o.endAt)) ?? [],
  );
  protected readonly when = computed(() => this.dates()[this.picked()] ?? '');
  /** The picked date's ticket link, else the event's own listing. */
  protected readonly sourceUrl = computed(() =>
    externalUrl(this.occurrence()?.sourceUrl ?? this.event()?.sourceUrl ?? null),
  );

  /** The picked date's forecast. Older API versions send only the first date's, at the top level. */
  protected readonly forecast = computed(() => {
    const e = this.event();
    const o = this.occurrence();
    if (!e || !o || this.online()) return null;
    return weatherView(o.weather ?? (this.picked() === 0 ? e.weather : null));
  });
  protected readonly weatherNote = computed(() => {
    const o = this.occurrence();
    return o ? weatherMessage(o.startAt) : '';
  });

  /** Venue, street, postal code and city on one line, or "Online". */
  protected readonly placeLine = computed(() => {
    const e = this.event();
    if (!e) return '';
    if (this.online()) return 'Online';
    return [e.venueName, e.address, [e.postalCode, e.cityName].filter(Boolean).join(' ')]
      .filter(Boolean)
      .join(', ');
  });

  /** The event's coordinates; null for online events and events without them. */
  private readonly coordinates = computed(() => {
    const e = this.event();
    return e && !this.online() && e.latitude !== null && e.longitude !== null
      ? { lat: e.latitude, lon: e.longitude }
      : null;
  });

  /** OpenStreetMap's embed page around the venue, with a marker on it. */
  protected readonly mapEmbed = computed(() => {
    const c = this.coordinates();
    if (!c) return null;
    const bbox = [c.lon - 0.006, c.lat - 0.003, c.lon + 0.006, c.lat + 0.003]
      .map((n) => n.toFixed(5))
      .join(',');
    // Safe to trust: a fixed host, and the query holds only numbers.
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${c.lat},${c.lon}`,
    );
  });
  protected readonly mapUrl = computed(() => {
    const c = this.coordinates();
    return c
      ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${c.lat},${c.lon}`)}`
      : null;
  });

  constructor() {
    effect(() => {
      const e = this.event();
      if (e) this.title.setTitle(`${e.title} – Loc`);
      else if (this.notFound()) this.title.setTitle('Event not found – Loc');
    });
  }

  /** Query params for the home list with only this filter set, via the same mapping the list uses. */
  protected filterParams(change: Partial<Filters>): QueryParams {
    return filtersToParams({ ...EMPTY_FILTERS, ...change }, amsterdamDate());
  }
}
