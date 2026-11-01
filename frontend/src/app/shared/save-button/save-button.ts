import { Component, inject, input, linkedSignal, signal } from '@angular/core';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSession } from '../../core/auth-session';
import { SavedEventsApi } from '../../core/saved-events-api';
import { IconComponent } from '../icon/icon';

/** Heart toggle for one event. Shows the new state at once and rolls back when the call fails. */
@Component({
  selector: 'app-save-button',
  imports: [IconComponent],
  template: `
    <button
      type="button"
      class="grid size-11 place-items-center rounded-full bg-panel text-blush-ink shadow-card"
      aria-label="Save event"
      [attr.aria-pressed]="saved()"
      [attr.aria-busy]="pending()"
      (click)="toggle()"
    >
      <app-icon [name]="saved() ? 'heart-filled' : 'heart'" />
    </button>
  `,
})
export class SaveButton {
  private readonly api = inject(SavedEventsApi);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  readonly eventId = input.required<string>();
  /** What the server said. A refetch resets the local state to it. */
  readonly initiallySaved = input.required<boolean>();

  protected readonly saved = linkedSignal(() => this.initiallySaved());
  protected readonly pending = signal(false);

  protected toggle(): void {
    if (!this.session.loggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (this.pending()) return; // one call at a time, so a double click can't race
    const next = !this.saved();
    this.saved.set(next);
    this.pending.set(true);
    (next ? this.api.save(this.eventId()) : this.api.unsave(this.eventId()))
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({ error: () => this.saved.set(!next) });
  }
}
