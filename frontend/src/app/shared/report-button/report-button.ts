import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  REPORT_REASON_LABELS,
  ReportReason,
  ReportTarget,
  ReportsApi,
} from '../../core/reports-api';

let nextId = 0;

/**
 * A Report button that opens a dialog with the fixed reasons and optional text. The native modal dialog
 * traps focus, closes on Escape and gives the focus back to the button.
 */
@Component({
  selector: 'app-report-button',
  imports: [ReactiveFormsModule],
  templateUrl: './report-button.html',
})
export class ReportButton {
  private readonly api = inject(ReportsApi);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly target = input.required<ReportTarget>();
  readonly targetId = input.required<string>();
  readonly buttonClass = input('link');

  protected readonly id = `report-${nextId++}`;
  protected readonly reasons = Object.entries(REPORT_REASON_LABELS).map(([value, label]) => ({
    value: value as ReportReason,
    label,
  }));
  // Same limits as the backend's ReportRequest.
  protected readonly form = new FormGroup({
    reason: new FormControl<ReportReason | null>(null, Validators.required),
    details: new FormControl('', { nonNullable: true, validators: Validators.maxLength(1000) }),
  });
  protected readonly sending = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected open(): void {
    this.form.reset();
    this.sent.set(false);
    this.error.set(null);
    this.dialog().nativeElement.showModal();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.sending()) return;
    this.sending.set(true);
    this.error.set(null);
    const { reason, details } = this.form.getRawValue();
    this.api
      .report(this.target(), this.targetId(), { reason: reason!, details: details.trim() || null })
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: () => this.sent.set(true),
        error: (err: HttpErrorResponse) => this.error.set(this.message(err)),
      });
  }

  protected fieldError(name: 'reason' | 'details'): string | null {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return null;
    return name === 'reason' ? 'Choose a reason.' : 'Use at most 1000 characters.';
  }

  private message(err: HttpErrorResponse): string {
    switch (err.status) {
      case 400:
        return (
          err.error?.errors?.reason ??
          err.error?.errors?.details ??
          err.error?.detail ??
          'Check your report and try again.'
        );
      case 404:
        return `This ${this.target()} is no longer available.`;
      case 429:
        return `You've sent a lot of reports. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`;
      default:
        return "We couldn't send your report. Check your connection and try again.";
    }
  }
}
