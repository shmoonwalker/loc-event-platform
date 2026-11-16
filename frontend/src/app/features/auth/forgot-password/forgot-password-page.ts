import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../../core/auth-api';

/** Asks for a password reset link; opened from the login page or the "you already have an account" email. */
@Component({
  selector: 'app-forgot-password-page',
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './forgot-password-page.html',
})
export class ForgotPasswordPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly authApi = inject(AuthApi);

  // The login page hands over its email in the navigation state, not the URL, so it stays out of history and logs.
  protected readonly form = this.fb.group({
    email: [
      (inject(Router).currentNavigation()?.extras.state?.['email'] as string | undefined) ?? '',
      [Validators.required, Validators.email, Validators.maxLength(254)],
    ],
  });

  /** form → sending (request in flight) → sent (show "check your email", whatever the email). */
  protected readonly status = signal<'form' | 'sending' | 'sent'>('form');

  /** A message about the whole form (rate limit, network), shown above the button. */
  protected readonly formError = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.status.set('sending');
    this.formError.set(null);
    this.authApi.forgotPassword(this.form.getRawValue().email).subscribe({
      next: () => this.status.set('sent'),
      error: (err: HttpErrorResponse) => {
        this.status.set('form');
        const fieldError: string | undefined =
          err.status === 400 ? err.error?.errors?.email : undefined;
        if (fieldError) {
          this.form.controls.email.setErrors({ server: fieldError });
          this.form.controls.email.markAsTouched();
        } else if (err.status === 429) {
          const seconds = err.headers.get('Retry-After') ?? '60';
          this.formError.set(`Too many attempts. Try again in ${seconds} seconds.`);
        } else {
          this.formError.set("We couldn't send the link. Check your connection and try again.");
        }
      },
    });
  }

  /** The message to show under the email field, or null while it is valid or not visited yet. */
  protected emailError(): string | null {
    const control = this.form.controls.email;
    if (!control.touched || !control.errors) return null;
    if (control.hasError('server')) return control.getError('server');
    return EMAIL_MESSAGES[Object.keys(control.errors)[0]] ?? null;
  }
}

const EMAIL_MESSAGES: Record<string, string> = {
  required: 'Enter your email.',
  email: 'Enter a valid email.',
  maxlength: 'Use at most 254 characters.',
};
