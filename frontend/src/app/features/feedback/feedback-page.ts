import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSession } from '../../core/auth-session';
import { FeedbackApi } from '../../core/feedback-api';
import { fieldError, showApiError } from '../account/form-errors';

/**
 * Website feedback for anyone. Visitors may leave an email to be answered on; a member's feedback
 * is linked to their account by the token, so they are not asked for one.
 */
@Component({
  selector: 'app-feedback-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './feedback-page.html',
})
export class FeedbackPage {
  private readonly api = inject(FeedbackApi);
  protected readonly session = inject(AuthSession);

  // Same limits as the backend's FeedbackRequest; \S also refuses a message of only spaces.
  protected readonly form = new FormGroup({
    message: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(2000)],
    }),
    contactEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.email, Validators.maxLength(254)],
    }),
  });
  protected readonly sending = signal(false);
  protected readonly sent = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected errorFor(name: 'message' | 'contactEmail'): string | null {
    return fieldError(this.form.controls[name], {
      required: 'Write a message first.',
      pattern: 'Write a message first.',
      maxlength:
        name === 'message' ? 'Use at most 2000 characters.' : 'Use at most 254 characters.',
      email: 'Enter a valid email.',
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.sending()) return;
    this.sending.set(true);
    this.formError.set(null);
    const { message, contactEmail } = this.form.getRawValue();
    const email = this.session.loggedIn() ? '' : contactEmail.trim();
    this.api
      .send(message.trim(), email || null)
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: () => this.sent.set(true),
        error: (err: HttpErrorResponse) => this.formError.set(this.apiError(err)),
      });
  }

  protected sendMore(): void {
    this.form.reset();
    this.sent.set(false);
  }

  private apiError(err: HttpErrorResponse): string | null {
    if (err.status === 429) {
      return `You've sent a lot of feedback. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`;
    }
    if (err.status === 400 && err.error?.errors) return showApiError(this.form, err);
    return "We couldn't send your feedback. Check your connection and try again.";
  }
}
