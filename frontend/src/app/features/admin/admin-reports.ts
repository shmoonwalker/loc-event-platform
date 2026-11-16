import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, finalize, forkJoin } from 'rxjs';
import { AdminApi, ReportGroup } from '../../core/admin-api';
import { REPORT_REASON_LABELS } from '../../core/reports-api';
import { valueOf } from '../../core/resource';
import { AdminError } from './admin-error';
import { AdminEventLink } from './admin-event-link';

/**
 * The open reports, one row per reported event or comment, newest activity first. Dismiss closes all of
 * an item's open reports without changing it; hiding an event takes it out of public lists and closes its
 * reports. After every action the list is fetched again.
 */
@Component({
  selector: 'app-admin-reports',
  imports: [ReactiveFormsModule, AdminError, AdminEventLink],
  templateUrl: './admin-reports.html',
})
export class AdminReports {
  private readonly api = inject(AdminApi);

  protected readonly reports = rxResource({ stream: () => this.api.reports() });
  protected readonly groups = computed(() => valueOf(this.reports));
  protected readonly reasonsText = (group: ReportGroup) =>
    group.reasons.map((r) => REPORT_REASON_LABELS[r]).join(', ');

  /** The item an action is running for, the event with an open hide form, and the last failure. */
  protected readonly busy = signal<{ id: string; action: 'hide' | 'dismiss' } | null>(null);
  protected readonly hiding = signal<string | null>(null);
  protected readonly failed = signal<{ id: string; message: string } | null>(null);
  // Same limits as the backend's HideEventRequest; \S refuses a reason of only spaces.
  protected readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(1000)],
  });

  protected openHide(group: ReportGroup): void {
    this.failed.set(null);
    this.reason.reset();
    this.hiding.set(group.targetId);
  }

  protected reasonError(): string | null {
    if (!this.reason.touched || !this.reason.errors) return null;
    return this.reason.hasError('maxlength')
      ? 'Use at most 1000 characters.'
      : 'Say why the event is hidden.';
  }

  protected hide(group: ReportGroup): void {
    if (this.reason.invalid) {
      this.reason.markAsTouched();
      return;
    }
    this.run(group, 'hide', this.api.hideEvent(group.targetId, this.reason.value.trim()), (err) =>
      err.status === 400
        ? (err.error?.errors?.reason ?? err.error?.detail ?? 'Check the reason and try again.')
        : err.status === 404
          ? 'This event no longer exists.'
          : "We couldn't hide this event. Check your connection and try again.",
    );
  }

  /** A report someone else already closed answers 404; the fresh list then shows what is left. */
  protected dismiss(group: ReportGroup): void {
    this.run(
      group,
      'dismiss',
      forkJoin(group.reportIds.map((id) => this.api.dismissReport(id))),
      (err) =>
        err.status === 404
          ? null
          : "We couldn't dismiss these reports. Check your connection and try again.",
    );
  }

  private run(
    group: ReportGroup,
    kind: 'hide' | 'dismiss',
    action: Observable<unknown>,
    message: (err: HttpErrorResponse) => string | null,
  ): void {
    if (this.busy()) return;
    this.busy.set({ id: group.targetId, action: kind });
    this.failed.set(null);
    action.pipe(finalize(() => this.busy.set(null))).subscribe({
      next: () => this.done(),
      error: (err: HttpErrorResponse) => {
        const text = message(err);
        if (text) this.failed.set({ id: group.targetId, message: text });
        else this.done();
      },
    });
  }

  private done(): void {
    this.hiding.set(null);
    this.reports.reload();
  }
}
