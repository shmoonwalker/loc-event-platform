import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuthSession } from '../../core/auth-session';
import { EventPhoto, PhotoReportReason, PhotosApi } from '../../core/photos-api';
import { valueOf } from '../../core/resource';
import { ErrorState } from '../../shared/error-state/error-state';

/**
 * Photos for one event. Approved photos are public; a member's own photos stay in a separate
 * list (a new upload is waiting, and is not mixed into the public grid). Picture URLs are the
 * ones the API returned.
 */
@Component({
  selector: 'app-event-photos',
  imports: [ReactiveFormsModule, ErrorState],
  templateUrl: './event-photos.html',
})
export class EventPhotos {
  private readonly api = inject(PhotosApi);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly session = inject(AuthSession);

  readonly eventId = input.required<string>();

  protected readonly reportReasons: { value: PhotoReportReason; label: string }[] = [
    { value: 'WRONG_INFORMATION', label: 'Wrong information' },
    { value: 'SPAM', label: 'Spam' },
    { value: 'INAPPROPRIATE', label: 'Inappropriate' },
    { value: 'OTHER', label: 'Other' },
  ];

  protected readonly publicPhotos = rxResource({
    params: () => this.eventId(),
    stream: ({ params }) => this.api.list(params),
  });
  /** Signing in loads the member's own photos; signing out leaves this idle. */
  protected readonly ownPhotos = rxResource({
    params: () => (this.session.loggedIn() ? this.eventId() : undefined),
    stream: ({ params }) => this.api.mine(params),
  });

  /** The public list, minus a photo deleted here since it loaded. */
  protected readonly photos = linkedSignal(() => valueOf(this.publicPhotos));
  /** The member's photos, plus one just uploaded and minus one just deleted. */
  protected readonly own = linkedSignal(() => valueOf(this.ownPhotos));
  private readonly ownIds = computed(() => new Set((this.own() ?? []).map((photo) => photo.id)));

  /** Approved photos that are not the current member's, so their own stay in the list above. */
  protected readonly grid = computed(() => {
    const list = this.photos();
    if (!list) return undefined;
    if (!this.session.loggedIn() || !this.ownPhotos.hasValue()) return list;
    const mine = this.ownIds();
    return list.filter((photo) => !mine.has(photo.id));
  });

  protected readonly uploading = signal(false);
  protected readonly uploadError = signal<string | null>(null);
  protected readonly confirmingDelete = signal<string | null>(null);
  protected readonly deleting = signal(false);
  protected readonly deleteError = signal<string | null>(null);
  protected readonly viewing = signal<EventPhoto | null>(null);
  protected readonly reporting = signal<EventPhoto | null>(null);
  protected readonly sendingReport = signal(false);
  protected readonly reportError = signal<string | null>(null);
  protected readonly reported = signal(false);

  protected readonly reportForm = this.fb.group({
    reason: this.fb.control<PhotoReportReason | ''>('', Validators.required),
    details: this.fb.control('', Validators.maxLength(1000)),
  });

  /** A report is only offered once we know the photo is not the member's. */
  protected canReport(photo: EventPhoto): boolean {
    return this.session.loggedIn() && this.ownPhotos.hasValue() && !this.ownIds().has(photo.id);
  }

  protected addPhoto(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.uploading()) return;
    this.uploading.set(true);
    this.uploadError.set(null);
    this.api
      .upload(this.eventId(), file)
      .pipe(finalize(() => this.uploading.set(false)))
      .subscribe({
        next: (photo) => {
          this.own.update((list) => [
            photo,
            ...(list ?? []).filter((item) => item.id !== photo.id),
          ]);
        },
        error: (err: HttpErrorResponse) => this.uploadError.set(uploadErrorText(err)),
      });
  }

  protected view(photo: EventPhoto, dialog: HTMLDialogElement): void {
    this.viewing.set(photo);
    if (!dialog.open) dialog.showModal();
  }

  protected askDelete(id: string): void {
    this.confirmingDelete.set(id);
    this.deleteError.set(null);
  }

  protected remove(id: string): void {
    if (this.deleting()) return;
    this.deleting.set(true);
    this.deleteError.set(null);
    this.api
      .delete(this.eventId(), id)
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: () => {
          this.own.update((list) => (list ?? []).filter((item) => item.id !== id));
          this.photos.update((list) => (list ?? []).filter((item) => item.id !== id));
          this.confirmingDelete.set(null);
        },
        error: () =>
          this.deleteError.set(
            "We couldn't delete your photo. Check your connection and try again.",
          ),
      });
  }

  protected openReport(photo: EventPhoto, dialog: HTMLDialogElement): void {
    this.reporting.set(photo);
    this.reportError.set(null);
    this.reported.set(false);
    this.reportForm.reset();
    if (!dialog.open) dialog.showModal();
  }

  protected sendReport(dialog: HTMLDialogElement): void {
    const photo = this.reporting();
    if (this.reportForm.invalid) {
      this.reportForm.markAllAsTouched();
      return;
    }
    if (!photo || this.sendingReport() || this.ownIds().has(photo.id)) return;
    const { reason, details } = this.reportForm.getRawValue();
    if (!reason) return;
    this.sendingReport.set(true);
    this.reportError.set(null);
    this.api
      .report(this.eventId(), photo.id, { reason, details: details.trim() })
      .pipe(finalize(() => this.sendingReport.set(false)))
      .subscribe({
        next: () => {
          this.reported.set(true);
          this.reporting.set(null);
          this.reportForm.reset();
          dialog.close();
        },
        error: (err: HttpErrorResponse) => this.reportError.set(reportErrorText(err)),
      });
  }

  /** The API's answer, else the first thing wrong with the form once it was submitted. */
  protected reportProblem(): string | null {
    if (this.reportError()) return this.reportError();
    const reason = this.reportForm.controls.reason;
    if (reason.touched && reason.invalid) return 'Please choose a reason.';
    const details = this.reportForm.controls.details;
    if (details.touched && details.hasError('maxlength')) return 'Use at most 1000 characters.';
    return null;
  }
}

const tooMany = (err: HttpErrorResponse) =>
  `Too many attempts. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`;

function uploadErrorText(err: HttpErrorResponse): string {
  switch (err.status) {
    case 400:
      return typeof err.error?.detail === 'string'
        ? err.error.detail
        : 'Choose a JPEG, PNG or WebP image.';
    case 413:
      return 'The picture must be 10 MB or smaller.';
    case 409:
      return 'You can add up to 10 photos for this event.';
    case 429:
      return tooMany(err);
    default:
      return "We couldn't upload your photo. Check your connection and try again.";
  }
}

function reportErrorText(err: HttpErrorResponse): string {
  switch (err.status) {
    case 400:
      return typeof err.error?.detail === 'string' ? err.error.detail : 'Please choose a reason.';
    case 404:
      return 'This photo is no longer available.';
    case 429:
      return tooMany(err);
    default:
      return "We couldn't send your report. Check your connection and try again.";
  }
}
