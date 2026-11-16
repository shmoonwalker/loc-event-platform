import {
  Component,
  ElementRef,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';
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
  /** The loaded page, minus the events unsaved here since; a reload starts from the server's answer again. */
  protected readonly result = linkedSignal(() => valueOf(this.saved));
  /** Announced in the count line after an unsave. */
  protected readonly removed = signal(false);
  private readonly status = viewChild.required<ElementRef<HTMLElement>>('status');
  protected readonly skeletons = Array.from({ length: 8 });

  /**
   * An unsaved event leaves the list at once (no undo: saving it again happens on the event's page).
   * When that empties the page, the previous page opens if this was the last one; otherwise the page reloads
   * so the next events move up.
   */
  protected remove(id: string): void {
    const page = this.result();
    if (!page) return;
    const totalElements = page.totalElements - 1;
    const totalPages = Math.ceil(totalElements / page.size);
    const items = page.items.filter((item) => item.event.id !== id);
    this.result.set({ ...page, items, totalElements, totalPages });
    this.removed.set(true);
    // The focused heart left with its card; keep keyboard focus on the page, where "Removed" is said.
    this.status().nativeElement.focus();
    if (items.length || !totalElements) return;
    if (this.page() >= totalPages) this.pageChange.emit(totalPages - 1);
    else this.saved.reload();
  }
}
