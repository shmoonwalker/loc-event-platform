import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EventsApi } from '../../../core/events-api';
import { FILTER_RESET, SORTS, SortKey, filtersToParams, hasActiveFilters } from '../../../core/filters';
import { valueOf } from '../../../core/resource';
import { amsterdamDate } from '../../../core/time';
import { ErrorState } from '../../../shared/error-state/error-state';
import { EventCardComponent } from '../../../shared/event-card/event-card';
import { EventCardSkeleton } from '../../../shared/event-card-skeleton/event-card-skeleton';
import { Pagination } from '../../../shared/pagination/pagination';
import { ListFilters } from '../list-filters';

const PAGE_SIZE = 12;

/** The results list: reads the filters from the URL, fetches one page, shows loading / error / empty / results. */
@Component({
  selector: 'app-event-list',
  imports: [RouterLink, EventCardComponent, EventCardSkeleton, ErrorState, Pagination],
  templateUrl: './event-list.html',
})
export class EventList {
  protected readonly store = inject(ListFilters);
  private readonly api = inject(EventsApi);

  protected readonly sorts = SORTS;
  protected readonly skeletons = Array.from({ length: 8 });

  // Whenever `params` (the filters signal) changes, `stream` runs again. Old requests are cancelled.
  protected readonly result = rxResource({
    params: () => this.store.filters(),
    stream: ({ params }) => this.api.events(filtersToParams(params, amsterdamDate()), PAGE_SIZE),
  });

  protected readonly filtered = computed(() => hasActiveFilters(this.store.filters()));
  protected readonly sort = computed<SortKey>(() => this.store.filters().sort ?? (this.store.filters().q ? 'relevance' : 'start_time'));
  protected readonly total = computed(() => valueOf(this.result)?.totalElements ?? 0);

  /** The backend answers 400 for combinations it rejects (hand-edited URLs). That is a "fix your filters" case. */
  protected readonly badRequest = computed(() => {
    const e = this.result.error();
    const cause = (e as { cause?: unknown } | undefined)?.cause ?? e;
    return cause instanceof HttpErrorResponse && cause.status === 400;
  });

  protected setSort(value: string): void {
    this.store.patch({ sort: value as SortKey });
  }

  protected setPage(page: number): void {
    this.store.patch({ page }, { push: true, fragment: 'all' });
  }

  protected clearFilters(): void {
    this.store.patch(FILTER_RESET);
  }
}
