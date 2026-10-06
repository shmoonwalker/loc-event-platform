import { Component, computed, input, output } from '@angular/core';
import { pageWindow } from './page-window';

/** Prev / numbers / next. It does not fetch anything: it only tells its parent which page was picked. */
@Component({
  selector: 'app-pagination',
  templateUrl: './pagination.html',
})
export class Pagination {
  readonly page = input.required<number>(); // zero-based
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();

  protected readonly items = computed(() => pageWindow(this.page(), this.totalPages()));
  protected readonly buttonClass =
    'grid min-h-11 min-w-11 place-items-center rounded-xl border border-line bg-panel font-semibold disabled:cursor-default disabled:opacity-50 aria-[current=page]:border-blush aria-[current=page]:bg-blush aria-[current=page]:text-on-blush';
}
