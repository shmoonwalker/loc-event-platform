import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { valueOf } from '../../core/resource';
import { SavedEventsApi } from '../../core/saved-events-api';
import { ErrorState } from '../../shared/error-state/error-state';
import { EventCardComponent } from '../../shared/event-card/event-card';
import { EventCardSkeleton } from '../../shared/event-card-skeleton/event-card-skeleton';
import { Pagination } from '../../shared/pagination/pagination';

/** The member's saved events, newest first. The page number lives in the URL (?page=). */
@Component({
  selector: 'app-saved-page',
  imports: [RouterLink, ErrorState, EventCardComponent, EventCardSkeleton, Pagination],
  templateUrl: './saved-page.html',
})
export class SavedPage {
  private readonly api = inject(SavedEventsApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** The ?page= query parameter, zero-based. Garbage means page 0. */
  readonly page = input<string>();
  protected readonly pageIndex = computed(() =>
    Math.max(0, Number.parseInt(this.page() ?? '', 10) || 0),
  );

  protected readonly saved = rxResource({
    params: () => this.pageIndex(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly result = computed(() => valueOf(this.saved));
  protected readonly skeletons = Array.from({ length: 8 });

  /** A new history entry per page, so Back returns to the previous page. Page 0 keeps the URL clean. */
  protected setPage(page: number): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { page: page || null } });
  }
}
