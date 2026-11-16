import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * An event in an admin list: its title, linked to its page while the public can see it. A hidden event's
 * page answers 404, so its title is plain text; an event missing from the catalog shows its id.
 */
@Component({
  selector: 'app-admin-event-link',
  imports: [RouterLink],
  template: `
    @if (!title()) {
      <span
        >Event <code class="text-xs text-admin-muted">{{ eventId() }}</code></span
      >
    } @else if (hidden()) {
      <span class="font-semibold">{{ title() }}</span>
      <span class="admin-tag ml-1.5 align-middle">Hidden</span>
    } @else {
      <a [routerLink]="['/events', eventId()]" class="admin-link">{{ title() }}</a>
    }
  `,
})
export class AdminEventLink {
  readonly eventId = input.required<string>();
  readonly title = input<string | null>(null);
  readonly hidden = input(false);
}
