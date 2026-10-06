import { Component, computed, effect, inject, input, linkedSignal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { EventsApi } from '../../core/events-api';
import { externalUrl } from '../../core/external-url';
import { OrganizerInfo } from '../../core/models';
import { errorStatus, valueOf } from '../../core/resource';
import { ErrorState } from '../../shared/error-state/error-state';
import { EventCardComponent } from '../../shared/event-card/event-card';
import { EventCardSkeleton } from '../../shared/event-card-skeleton/event-card-skeleton';
import { Pagination } from '../../shared/pagination/pagination';
import { NotFoundState } from '../../shared/not-found-state/not-found-state';

/** An organizer and their upcoming events. The page number lives in the URL (?page=), like the home list. */
@Component({
  selector: 'app-organizer-page',
  imports: [ErrorState, EventCardComponent, EventCardSkeleton, Pagination, NotFoundState],
  templateUrl: './organizer-page.html',
})
export class OrganizerPage {
  private readonly api = inject(EventsApi);
  private readonly title = inject(Title);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** The :id route parameter. */
  readonly id = input.required<string>();
  /** The ?page= query parameter, zero-based. Garbage means page 0. */
  readonly page = input<string>();

  protected readonly pageIndex = computed(() =>
    Math.max(0, Number.parseInt(this.page() ?? '', 10) || 0),
  );
  protected readonly skeletons = Array.from({ length: 8 });

  protected readonly detail = rxResource({
    params: () => ({ id: this.id(), page: this.pageIndex() }),
    stream: ({ params }) => this.api.organizer(params.id, params.page),
  });

  protected readonly events = computed(() => valueOf(this.detail)?.events);
  protected readonly notFound = computed(() => errorStatus(this.detail) === 404);

  // The resource forgets its value while the next page loads; keep showing the same organizer meanwhile.
  protected readonly organizer = linkedSignal<OrganizerInfo | undefined, OrganizerInfo | undefined>(
    {
      source: () => valueOf(this.detail)?.organizer,
      computation: (next, prev) => next ?? (prev?.value?.id === this.id() ? prev.value : undefined),
    },
  );
  protected readonly site = computed(() => externalUrl(this.organizer()?.site ?? null));

  constructor() {
    effect(() => {
      const o = this.organizer();
      if (o) this.title.setTitle(`${o.name} – Loc`);
      else if (this.notFound()) this.title.setTitle('Organizer not found – Loc');
    });
  }

  /** A new history entry per page, so Back returns to the previous page. Page 0 keeps the URL clean. */
  protected setPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page || null },
      fragment: 'events',
    });
  }
}
