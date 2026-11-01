import { Component, computed, inject, input } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthSession } from '../../core/auth-session';
import { FollowedOrganizers } from './followed-organizers';
import { SavedEvents } from './saved-events';

export type MeTab = 'saved' | 'following';

/**
 * The member's own page: saved events and followed organizers, one tab each.
 * The tab and the page number live in the URL (?tab=following&page=2), so reload and Back keep them.
 * The tabs are links, not an ARIA tablist: each one is a real URL.
 */
@Component({
  selector: 'app-me-page',
  imports: [RouterLink, SavedEvents, FollowedOrganizers],
  templateUrl: './me-page.html',
})
export class MePage {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly session = inject(AuthSession);

  /** The ?tab= query parameter. Anything else means the Saved tab. */
  readonly tab = input<string>();
  /** The ?page= query parameter for the Saved tab, zero-based. Garbage means page 0. */
  readonly page = input<string>();

  protected readonly current = computed<MeTab>(() =>
    this.tab() === 'following' ? 'following' : 'saved',
  );
  protected readonly pageIndex = computed(() =>
    Math.max(0, Number.parseInt(this.page() ?? '', 10) || 0),
  );

  protected readonly tabs: { id: MeTab; label: string }[] = [
    { id: 'saved', label: 'Saved' },
    { id: 'following', label: 'Following' },
  ];

  /** A new history entry per page, so Back returns to the previous page. Page 0 keeps the URL clean. */
  protected setPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page || null },
      queryParamsHandling: 'merge',
    });
  }
}
