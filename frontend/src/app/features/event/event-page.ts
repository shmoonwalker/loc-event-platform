import { Component, computed, effect, inject, input, linkedSignal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { categoryImage } from '../../core/event-image';
import { EventsApi, QueryParams } from '../../core/events-api';
import { externalUrl } from '../../core/external-url';
import { EMPTY_FILTERS, Filters, filtersToParams } from '../../core/filters';
import { errorStatus, valueOf } from '../../core/resource';
import { amsterdamDate, formatWhen } from '../../core/time';
import { ErrorState } from '../../shared/error-state/error-state';
import { IconComponent } from '../../shared/icon/icon';
import { NotFoundState } from '../../shared/not-found-state/not-found-state';
import { weatherView } from './weather';

/** One event: everything the API knows about it. 404 (unknown or already over) gets a calm not-found state. */
@Component({
  selector: 'app-event-page',
  imports: [RouterLink, IconComponent, ErrorState, NotFoundState],
  templateUrl: './event-page.html',
})
export class EventPage {
  private readonly api = inject(EventsApi);
  private readonly title = inject(Title);

  /** The :id route parameter. Changing it (a link to another event) refetches. */
  readonly id = input.required<string>();

  protected readonly detail = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.event(params),
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
  protected readonly placeLine = computed(() => {
    const e = this.event();
    return this.online() ? 'Online' : [e?.venueName, e?.cityName].filter(Boolean).join(', ');
  });
  protected readonly when = computed(() => {
    const first = this.event()?.occurrences[0];
    return first ? formatWhen(first.startAt, first.endAt) : '';
  });
  protected readonly moreDates = computed(
    () =>
      this.event()
        ?.occurrences.slice(1)
        .map((o) => formatWhen(o.startAt, o.endAt)) ?? [],
  );
  protected readonly weather = computed(() => weatherView(this.event()?.weather ?? null));
  protected readonly sourceUrl = computed(() => externalUrl(this.event()?.sourceUrl ?? null));

  /** Venue, street and city, as lines. Empty for online events. */
  protected readonly addressLines = computed(() => {
    const e = this.event();
    if (!e || this.online()) return [];
    return [e.venueName, e.address, [e.postalCode, e.cityName].filter(Boolean).join(' ')].filter(
      (line): line is string => !!line,
    );
  });

  /** A plain maps link instead of an embedded map: no extra dependency. Coordinates win, they are exact. */
  protected readonly mapUrl = computed(() => {
    const e = this.event();
    if (!e || this.online()) return null;
    const query =
      e.latitude !== null && e.longitude !== null
        ? `${e.latitude},${e.longitude}`
        : this.addressLines().join(', ');
    return query
      ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`
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
