import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { AdminApi, FeedbackPage } from '../../core/admin-api';
import { AuthSession } from '../../core/auth-session';
import { valueOf } from '../../core/resource';
import { ADMIN_SECTIONS } from './admin-sections';

/** The admin panel's start page: one card per section, with how many items wait there. */
@Component({
  selector: 'app-admin-overview',
  imports: [RouterLink],
  template: `
    <h1 class="text-2xl font-bold">Overview</h1>
    <p class="mt-1 text-admin-muted">
      Signed in as {{ session.currentUser()?.displayName }}. Changes here affect what every visitor
      sees.
    </p>

    @if (sections.length) {
      <ul role="list" class="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        @for (s of sections; track s.path) {
          <li>
            <a
              [routerLink]="s.path"
              class="flex h-full flex-col rounded-md border border-admin-line bg-admin-surface p-4 hover:border-admin-accent"
            >
              <span class="flex items-baseline justify-between gap-3">
                <span class="font-semibold">{{ s.label }}</span>
                @if (counts()[s.path]; as count) {
                  <span class="text-2xl font-bold text-admin-accent">{{ count }}</span>
                }
              </span>
              <span class="mt-1 text-sm text-admin-muted">{{ s.description }}</span>
            </a>
          </li>
        }
      </ul>
    } @else {
      <p class="mt-6 rounded-md border border-dashed border-admin-line p-6 text-admin-muted">
        No admin sections yet.
      </p>
    }
  `,
})
export class AdminOverview {
  private readonly api = inject(AdminApi);
  protected readonly session = inject(AuthSession);
  protected readonly sections = ADMIN_SECTIONS;

  private readonly reports = rxResource({ stream: () => this.api.reports() });
  private readonly photos = rxResource({ stream: () => this.api.photos('PENDING') });
  private readonly hidden = rxResource({ stream: () => this.api.hiddenEvents() });
  private readonly feedback = rxResource({ stream: () => this.api.feedback(0) });

  /** Per section path; missing while loading or after an error, and zero shows no number. */
  protected readonly counts = computed<Record<string, number | string | undefined>>(() => ({
    reports: valueOf(this.reports)?.length,
    photos: valueOf(this.photos)?.length,
    'hidden-events': valueOf(this.hidden)?.length,
    feedback: openFeedback(valueOf(this.feedback)),
  }));
}

/**
 * Open feedback comes first, so the open items on the first page are all of them, unless the whole
 * first page is open and more follow. shortcut: shows "20+" then; a count from the API would be exact.
 */
function openFeedback(page: FeedbackPage | undefined): number | string | undefined {
  if (!page) return undefined;
  const open = page.items.filter((f) => !f.handledAt).length;
  return open === page.items.length && page.totalPages > 1 ? `${open}+` : open;
}
