import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EventRail } from '../../../core/models';
import { EventCardComponent } from '../../../shared/event-card/event-card';
import { railText } from '../rail-text';

/** One horizontal row of up to six event cards, with a "See all" link into the list. */
@Component({
  selector: 'app-rail',
  imports: [RouterLink, EventCardComponent],
  templateUrl: './rail.html',
})
export class RailComponent {
  readonly rail = input.required<EventRail>();
  readonly cityName = input.required<string>();

  protected readonly text = computed(() => railText(this.rail().mode, this.cityName()));
  protected readonly headingId = computed(() => `rail-${this.rail().mode}-heading`);
}
