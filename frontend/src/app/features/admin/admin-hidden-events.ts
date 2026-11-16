import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AdminApi } from '../../core/admin-api';
import { valueOf } from '../../core/resource';
import { formatWhen } from '../../core/time';
import { AdminError } from './admin-error';

/**
 * Events an admin took out of public lists: title, who, when and why, most recent first, with Unhide.
 * The title is not linked: the public event page answers 404 while the event is hidden.
 */
@Component({
  selector: 'app-admin-hidden-events',
  imports: [AdminError],
  templateUrl: './admin-hidden-events.html',
})
export class AdminHiddenEvents {
  private readonly api = inject(AdminApi);

  protected readonly hidden = rxResource({ stream: () => this.api.hiddenEvents() });
  protected readonly events = computed(() => valueOf(this.hidden));
  protected readonly when = (iso: string) => formatWhen(iso, iso);

  protected readonly busy = signal<string | null>(null);
  protected readonly failed = signal<string | null>(null);

  protected unhide(eventId: string): void {
    if (this.busy()) return;
    this.busy.set(eventId);
    this.failed.set(null);
    this.api
      .unhideEvent(eventId)
      .pipe(finalize(() => this.busy.set(null)))
      .subscribe({
        next: () => this.hidden.reload(),
        error: () => this.failed.set(eventId),
      });
  }
}
