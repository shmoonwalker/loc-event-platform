import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AdminApi } from '../../core/admin-api';
import { valueOf } from '../../core/resource';
import { formatWhen } from '../../core/time';
import { Pagination } from '../../shared/pagination/pagination';
import { AdminError } from './admin-error';

/** Website feedback, open items first and then the newest, one page at a time, with Mark handled. */
@Component({
  selector: 'app-admin-feedback',
  imports: [AdminError, Pagination],
  templateUrl: './admin-feedback.html',
})
export class AdminFeedback {
  private readonly api = inject(AdminApi);

  /** Zero-based. */
  protected readonly page = signal(0);
  protected readonly feedback = rxResource({
    params: () => this.page(),
    stream: ({ params }) => this.api.feedback(params),
  });
  protected readonly result = computed(() => valueOf(this.feedback));
  protected readonly when = (iso: string) => formatWhen(iso, iso);

  protected readonly busy = signal<string | null>(null);
  protected readonly failed = signal<string | null>(null);

  protected markHandled(id: string): void {
    if (this.busy()) return;
    this.busy.set(id);
    this.failed.set(null);
    this.api
      .markFeedbackHandled(id)
      .pipe(finalize(() => this.busy.set(null)))
      .subscribe({
        next: () => this.feedback.reload(),
        error: () => this.failed.set(id),
      });
  }
}
