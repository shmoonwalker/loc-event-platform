import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthApi } from '../../../core/auth-api';
import { AuthSession } from '../../../core/auth-session';

/** Log-in page: email and password. */
@Component({
  selector: 'app-login-page',
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './login-page.html',
})
export class LoginPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly authApi = inject(AuthApi);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  // Password is only "required": the strength rules belong to sign-up, not to checking an existing password.
  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', Validators.required],
  });

  protected readonly showPassword = signal(false);
  protected readonly sending = signal(false);

  /** A message about the whole form (wrong credentials, rate limit, network), shown above the button. */
  protected readonly formError = signal<string | null>(null);

  /** Set after a 403: the email that still needs verifying, so the page can offer a new link for it. */
  protected readonly unverifiedEmail = signal<string | null>(null);
  protected readonly resendStatus = signal<'idle' | 'sending' | 'sent'>('idle');

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    this.formError.set(null);
    this.unverifiedEmail.set(null);
    this.resendStatus.set('idle');
    const credentials = this.form.getRawValue();
    this.authApi.login(credentials).subscribe({
      next: (response) => {
        this.session.start(response.accessToken);
        void this.router.navigateByUrl('/');
      },
      error: (err: HttpErrorResponse) => {
        this.sending.set(false);
        this.form.controls.password.reset();
        this.formError.set(loginErrorMessage(err));
        if (err.status === 403) this.unverifiedEmail.set(credentials.email);
      },
    });
  }

  /** The message to show under a field, or null while it is valid or not visited yet. */
  protected errorFor(field: 'email' | 'password'): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) return null;
    return MESSAGES[field][Object.keys(control.errors)[0]] ?? null;
  }

  /** The API always answers 202, so this never reveals more than the 403 already did. */
  protected resend(email: string): void {
    this.resendStatus.set('sending');
    this.formError.set(null);
    this.authApi.resendVerification(email).subscribe({
      next: () => this.resendStatus.set('sent'),
      error: (err: HttpErrorResponse) => {
        this.resendStatus.set('idle');
        this.formError.set(
          err.status === 429
            ? `Too many attempts. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`
            : "We couldn't send the link. Check your connection and try again.",
        );
      },
    });
  }
}

const MESSAGES: Record<string, Record<string, string>> = {
  email: {
    required: 'Enter your email.',
    email: 'Enter a valid email.',
    maxlength: 'Use at most 254 characters.',
  },
  password: {
    required: 'Enter your password.',
  },
};

function loginErrorMessage(err: HttpErrorResponse): string {
  switch (err.status) {
    case 401:
      // Same text for an unknown email and a wrong password, like the API.
      return 'Email or password is incorrect.';
    case 403:
      return 'Verify your email first. Open the link we sent you, then log in.';
    case 429:
      return `Too many attempts. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`;
    default:
      return "We couldn't log you in. Check your connection and try again.";
  }
}
