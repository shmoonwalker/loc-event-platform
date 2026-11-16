import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EventsApi } from '../../core/events-api';
import { valueOf } from '../../core/resource';

/**
 * An event in an admin list: its title, linked to its page. The admin lists only carry the id,
 * so the title comes from the public event API; a hidden or removed event shows its id instead.
 */
@Component({
  selector: 'app-admin-event-link',
  imports: [RouterLink],
  template: `
    @if (title(); as t) {
      <a [routerLink]="['/events', eventId()]" class="admin-link">{{ t }}</a>
    } @else if (event.error()) {
      <span
        >Event <code class="text-xs text-admin-muted">{{ eventId() }}</code></span
      >
    } @else {
      <span class="text-admin-muted">Loading…</span>
    }
  `,
})
export class AdminEventLink {
  private readonly api = inject(EventsApi);
  readonly eventId = input.required<string>();

  protected readonly event = rxResource({
    params: () => this.eventId(),
    stream: ({ params }) => this.api.event(params),
  });
  protected readonly title = computed(() => valueOf(this.event)?.title);
}
