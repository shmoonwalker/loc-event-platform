import { Component, computed, inject, input, output } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { valueOf } from '../../core/resource';
import { SavedEventsApi } from '../../core/saved-events-api';
import { ErrorState } from '../../shared/error-state/error-state';
import { EventCardComponent } from '../../shared/event-card/event-card';
import { EventCardSkeleton } from '../../shared/event-card-skeleton/event-card-skeleton';
import { Pagination } from '../../shared/pagination/pagination';

/** The "Saved" tab: the member's saved events, newest first. The page number belongs to the parent's URL. */
@Component({
  selector: 'app-saved-events',
  imports: [RouterLink, ErrorState, EventCardComponent, EventCardSkeleton, Pagination],
  templateUrl: './saved-events.html',
})
export class SavedEvents {
  private readonly api = inject(SavedEventsApi);

  /** Zero-based page index. */
  readonly page = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly saved = rxResource({
    params: () => this.page(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly result = computed(() => valueOf(this.saved));
  protected readonly skeletons = Array.from({ length: 8 });
}
