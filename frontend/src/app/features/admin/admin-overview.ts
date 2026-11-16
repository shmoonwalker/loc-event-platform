import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthSession } from '../../core/auth-session';
import { ADMIN_SECTIONS } from './admin-sections';

/** The admin panel's start page: one card per section. */
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
              <span class="font-semibold">{{ s.label }}</span>
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
  protected readonly session = inject(AuthSession);
  protected readonly sections = ADMIN_SECTIONS;
}
