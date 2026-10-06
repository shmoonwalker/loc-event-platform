import { Injectable, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';
import { Filters, filtersToParams, paramsToFilters } from '../../core/filters';
import { amsterdamDate } from '../../core/time';

/**
 * The list's filter state, stored in the URL.
 *  - `filters` is a signal derived FROM the URL, so the URL is the single source of truth.
 *  - `patch()` writes changes BACK to the URL; the signal then updates by itself.
 * It is provided by HomePage (not root), so each visit to the page gets a fresh instance whose
 * ActivatedRoute is the home route. Like a Spring bean with a narrower scope than singleton.
 */
@Injectable()
export class ListFilters {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly filters = toSignal(
    this.route.queryParamMap.pipe(map((params) => paramsToFilters(params, amsterdamDate()))),
    { requireSync: true }, // queryParamMap emits immediately, so the signal always has a value
  );

  /**
   * Change some filters. Any change goes back to page 0 unless `change` says otherwise.
   * By default the browser history entry is replaced (so Back does not step through every chip click);
   * pass push: true for deliberate actions like submitting a search or changing page.
   */
  patch(change: Partial<Filters>, options: { push?: boolean; fragment?: string } = {}): void {
    const next: Filters = { ...this.filters(), page: 0, ...change };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: filtersToParams(next, amsterdamDate()),
      fragment: options.fragment,
      replaceUrl: !options.push,
    });
  }
}
