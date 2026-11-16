import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, finalize } from 'rxjs';

let nextId = 0;

/** One text box for a comment, reply or edit. The parent says what saving means; errors show under the box. */
@Component({
  selector: 'app-comment-form',
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="form" class="flex max-w-[70ch] flex-col gap-1.5" (ngSubmit)="submit()">
      <label [for]="id" class="font-semibold">{{ label() }}</label>
      <textarea
        [id]="id"
        formControlName="body"
        rows="3"
        class="field-input py-2"
        [attr.aria-describedby]="id + '-error'"
        [attr.aria-invalid]="error() ? true : null"
      ></textarea>
      <p [id]="id + '-error'" class="text-sm text-danger" aria-live="polite">{{ error() }}</p>
      <div class="flex items-center gap-4">
        <button type="submit" class="btn-dark" [disabled]="sending()">
          {{ sending() ? 'Sending…' : submitLabel() }}
        </button>
        @if (cancelable()) {
          <button type="button" class="link" (click)="cancelled.emit()">Cancel</button>
        }
      </div>
    </form>
  `,
})
export class CommentForm implements OnInit {
  readonly label = input.required<string>();
  readonly submitLabel = input.required<string>();
  /** The text to start from, for an edit. */
  readonly initial = input('');
  readonly cancelable = input(false);
  /** Sends the trimmed text to the API. */
  readonly save = input.required<(body: string) => Observable<unknown>>();
  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly id = `comment-form-${nextId++}`;
  // Same limits as the backend's CommentRequest; \S also refuses text that is only spaces.
  protected readonly form = new FormGroup({
    body: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(2000)],
    }),
  });
  protected readonly body = this.form.controls.body;
  protected readonly sending = signal(false);
  private readonly serverError = signal<string | null>(null);

  ngOnInit(): void {
    this.body.setValue(this.initial());
  }

  protected submit(): void {
    if (this.body.invalid) {
      this.body.markAsTouched();
      return;
    }
    if (this.sending()) return;
    this.sending.set(true);
    this.serverError.set(null);
    this.save()(this.body.value.trim())
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: () => {
          this.body.reset();
          this.saved.emit();
        },
        error: (err: HttpErrorResponse) => this.serverError.set(commentErrorMessage(err)),
      });
  }

  /** The API's answer, else the first validation message once the box was visited. */
  protected error(): string | null {
    if (this.serverError()) return this.serverError();
    if (!this.body.touched || !this.body.errors) return null;
    return this.body.hasError('maxlength')
      ? 'Use at most 2000 characters.'
      : 'Write something first.';
  }
}

function commentErrorMessage(err: HttpErrorResponse): string {
  switch (err.status) {
    case 400:
      return err.error?.errors?.body ?? err.error?.detail ?? 'Check your comment and try again.';
    case 404:
      return 'This comment is no longer available.';
    case 429:
      return `You're commenting too fast. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`;
    default:
      return "We couldn't send your comment. Check your connection and try again.";
  }
}
