import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { dayParts, formatTime } from '../../core/time';
import { EventCard } from '../../core/models';
import { IconComponent } from '../icon/icon';

// Soft placeholder colours for events without an image (full class names so Tailwind can find them).
const TINTS = ['bg-tint-a', 'bg-tint-b', 'bg-tint-c'];

/** The one event card, used by the rails and by the list. Input in, markup out: no state, no HTTP. */
@Component({
  selector: 'app-event-card',
  imports: [RouterLink, IconComponent],
  templateUrl: './event-card.html',
})
export class EventCardComponent {
  readonly event = input.required<EventCard>();

  protected readonly online = computed(() => this.event().place === 'ONLINE');
  protected readonly day = computed(() => dayParts(this.event().startAt));
  protected readonly tint = computed(() => TINTS[this.event().id.split('').reduce((n, c) => n + c.charCodeAt(0), 0) % 3]);

  /** "20:30 – 23:00", or just "20:30" when the event has no end time. */
  protected readonly time = computed(() => {
    const { startAt, endAt } = this.event();
    return startAt === endAt ? formatTime(startAt) : `${formatTime(startAt)} – ${formatTime(endAt)}`;
  });

  protected readonly where = computed(() => {
    const e = this.event();
    return this.online() ? 'Online' : [e.venueName, e.cityName].filter(Boolean).join(', ');
  });
}
