import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../../core/auth-api';
import { AuthSession } from '../../../core/auth-session';
import { newPasswordValidators } from '../../../core/password-rules';

/** Opened from the password reset email; sets a new password with the link's token. */
@Component({
  selector: 'app-reset-password-page',
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './reset-password-page.html',
})
export class ResetPasswordPage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(AuthSession);

  /** The ?token= query parameter from the email link. */
  readonly token = input<string>();

  protected readonly form = this.fb.group({ newPassword: ['', newPasswordValidators] });

  protected readonly showPassword = signal(false);

  /** form → sending → done; invalid when the link has no token or the API rejects it. */
  protected readonly status = signal<'form' | 'sending' | 'done' | 'invalid'>('form');

  /** A message about the whole form (network), shown above the button. */
  protected readonly formError = signal<string | null>(null);

  ngOnInit(): void {
    if (!this.token()) this.status.set('invalid');
  }

  protected submit(): void {
    const token = this.token();
    if (!token) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.status.set('sending');
    this.formError.set(null);
    this.authApi.resetPassword(token, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.form.reset();
        this.showPassword.set(false);
        // The server has signed out every session of this account, this browser's included.
        if (this.session.loggedIn()) this.session.end();
        this.status.set('done');
      },
      error: (err: HttpErrorResponse) => {
        this.status.set('form');
        this.showApiError(err);
      },
    });
  }

  /** Password field errors go under the field; an unknown, expired or used token shows the "invalid link" state. */
  private showApiError(err: HttpErrorResponse): void {
    if (err.status !== 400) {
      this.formError.set(
        err.status === 429
          ? `Too many attempts. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`
          : "We couldn't change your password. Check your connection and try again.",
      );
      return;
    }
    const fieldErrors: Record<string, string> | undefined = err.error?.errors;
    // The backend's 72-byte check is a method, so its error key is not "newPassword".
    const message = fieldErrors?.['newPassword'] ?? fieldErrors?.['newPasswordWithinByteLimit'];
    if (!message) {
      this.status.set('invalid');
      return;
    }
    this.form.controls.newPassword.setErrors({ server: message });
    this.form.controls.newPassword.markAsTouched();
  }

  /** The message to show under the password field, or null while it is valid or not visited yet. */
  protected passwordError(): string | null {
    const control = this.form.controls.newPassword;
    if (!control.touched || !control.errors) return null;
    if (control.hasError('server')) return control.getError('server');
    return PASSWORD_MESSAGES[Object.keys(control.errors)[0]] ?? null;
  }
}

const PASSWORD_MESSAGES: Record<string, string> = {
  required: 'Enter a new password.',
  minlength: 'Use at least 10 characters.',
  maxBytes: 'This password is too long.',
};
