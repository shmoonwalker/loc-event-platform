import { Component } from '@angular/core';

/** Grey placeholder shaped like an event card, shown while data loads. Decorative, so hidden from screen readers. */
@Component({
  selector: 'app-event-card-skeleton',
  host: { 'aria-hidden': 'true', class: 'block' },
  template: `
    <div class="overflow-hidden rounded-[18px] border border-line bg-panel motion-safe:animate-pulse">
      <div class="h-40 bg-line/60"></div>
      <div class="flex flex-col gap-2.5 p-4">
        <div class="h-4 w-4/5 rounded bg-line/60"></div>
        <div class="h-3.5 w-2/5 rounded bg-line/60"></div>
        <div class="h-3.5 w-3/5 rounded bg-line/60"></div>
      </div>
    </div>
  `,
})
export class EventCardSkeleton {}
