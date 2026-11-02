import { Component, inject, input, linkedSignal, signal } from '@angular/core';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSession } from '../../core/auth-session';
import { FollowedOrganizersApi } from '../../core/followed-organizers-api';
import { IconComponent } from '../icon/icon';

/**
 * Follow toggle for one organizer, same behaviour as the save button: shows the new state at once
 * and rolls back when the call fails. The label stays "Follow"; aria-pressed and the check carry the state.
 */
@Component({
  selector: 'app-follow-button',
  imports: [IconComponent],
  template: `
    <button
      type="button"
      class="pill-btn aria-pressed:border-ink aria-pressed:bg-ink aria-pressed:text-panel"
      [attr.aria-pressed]="followed()"
      [attr.aria-busy]="pending()"
      (click)="toggle()"
    >
      @if (followed()) {
        <app-icon name="check" />
      }
      Follow
    </button>
  `,
})
export class FollowButton {
  private readonly api = inject(FollowedOrganizersApi);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  readonly organizerId = input.required<string>();
  /** What the server said. A refetch resets the local state to it. */
  readonly initiallyFollowed = input.required<boolean>();

  protected readonly followed = linkedSignal(() => this.initiallyFollowed());
  protected readonly pending = signal(false);

  protected toggle(): void {
    if (!this.session.loggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    if (this.pending()) return; // one call at a time, so a double click can't race
    const next = !this.followed();
    this.followed.set(next);
    this.pending.set(true);
    (next ? this.api.follow(this.organizerId()) : this.api.unfollow(this.organizerId()))
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({ error: () => this.followed.set(!next) });
  }
}
