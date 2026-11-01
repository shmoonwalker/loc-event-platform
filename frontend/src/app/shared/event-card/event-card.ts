import { Component, computed, input, linkedSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { categoryImage, eventImageUrl } from '../../core/event-image';
import { dayParts, formatTime } from '../../core/time';
import { EventCard } from '../../core/models';
import { SaveButton } from '../save-button/save-button';

/** The one event card, used by the rails and by the lists. Input in, markup out; only its save button talks to the API. */
@Component({
  selector: 'app-event-card',
  imports: [RouterLink, SaveButton],
  templateUrl: './event-card.html',
})
export class EventCardComponent {
  readonly event = input.required<EventCard>();
  /** Saved events that were withdrawn or are over: still shown on the Saved page, marked as gone. */
  readonly unavailable = input(false);

  protected readonly online = computed(() => this.event().place === 'ONLINE');
  protected readonly day = computed(() => dayParts(this.event().startAt));
  /** A listing image that fails to load (dead link, hotlink block) falls back to the category photo. */
  protected readonly imageFailed = linkedSignal({ source: this.event, computation: () => false });
  protected readonly imageUrl = computed(() =>
    this.imageFailed() ? categoryImage(this.event().category) : eventImageUrl(this.event()),
  );

  /** "20:30 – 23:00", or just "20:30" when the event has no end time. */
  protected readonly time = computed(() => {
    const { startAt, endAt } = this.event();
    return startAt === endAt
      ? formatTime(startAt)
      : `${formatTime(startAt)} – ${formatTime(endAt)}`;
  });

  protected readonly where = computed(() => {
    const e = this.event();
    return this.online() ? 'Online' : [e.venueName, e.cityName].filter(Boolean).join(', ');
  });
}
