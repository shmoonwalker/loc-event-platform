import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EventsApi } from '../../core/events-api';
import { HomeCity } from '../../core/home-city';
import { valueOf } from '../../core/resource';
import { ErrorState } from '../../shared/error-state/error-state';
import { EventCardSkeleton } from '../../shared/event-card-skeleton/event-card-skeleton';
import { EventList } from './event-list/event-list';
import { ListFilters } from './list-filters';
import { RailComponent } from './rail/rail';
import { railText } from './rail-text';
import { SearchConsole } from './search-console/search-console';

/**
 * The public home page: hero + search console, highlight rails, then the full filtered list.
 * `providers: [ListFilters]` creates the URL-backed filter state for this page and its children.
 */
@Component({
  selector: 'app-home-page',
  imports: [RouterLink, SearchConsole, RailComponent, EventList, ErrorState, EventCardSkeleton],
  providers: [ListFilters],
  templateUrl: './home-page.html',
})
export class HomePage {
  private readonly api = inject(EventsApi);
  private readonly homeCity = inject(HomeCity);

  // Refetches by itself whenever the chosen home city changes.
  protected readonly home = rxResource({
    params: () => this.homeCity.slug(),
    stream: ({ params }) => this.api.home(params),
  });

  protected readonly rails = computed(() => valueOf(this.home)?.rails ?? []);
  protected readonly cityName = computed(() => valueOf(this.home)?.nearYouCity.name ?? '');
  protected readonly skeletons = Array.from({ length: 4 });

  /** "Jump to" links only for rails that exist (the backend omits empty rails). */
  protected readonly jumpLinks = computed(() =>
    this.rails().map((r) => ({ id: `rail-${r.mode}`, label: railText(r.mode, this.cityName()).title, live: r.mode === 'TONIGHT' })),
  );
}
