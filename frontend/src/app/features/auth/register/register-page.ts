import { Component, inject, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../../core/auth-api';
import { HttpErrorResponse } from '@angular/common/http';

/** Sign-up page: email, password and display name. */
@Component({
  selector: 'app-register-page',
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './register-page.html',
})
export class RegisterPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly authApi = inject(AuthApi);

  // Same rules as the backend's RegisterRequest, so most mistakes are caught before a request.
  protected readonly form = this.fb.group({
    displayName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(10), maxBytes(72)]],
  });

  protected readonly showPassword = signal(false);

  /** form → sending (request in flight) → sent (show "check your email"). */
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
    this.authApi.register(this.form.getRawValue()).subscribe({
      next: () => {
        this.form.controls.password.reset();
        this.showPassword.set(false);
        this.status.set('sent');
      },
      error: (err: HttpErrorResponse) => {
        this.status.set('form');
        this.showApiError(err);
      },
    });
  }
  /** Puts the API's field errors under their fields; anything else becomes one message above the button. */
  private showApiError(err: HttpErrorResponse): void {
    const fieldErrors: Record<string, string> | undefined =
      err.status === 400 ? err.error?.errors : undefined;
    if (fieldErrors) {
      for (const [key, message] of Object.entries(fieldErrors)) {
        // The backend's 72-byte check is a method, so its error key is not "password".
        const control = this.form.get(key === 'passwordWithinByteLimit' ? 'password' : key);
        if (control) {
          control.setErrors({ server: message });
          control.markAsTouched();
        } else {
          this.formError.set(message);
        }
      }
      return;
    }
    if (err.status === 429) {
      const seconds = err.headers.get('Retry-After') ?? '60';
      this.formError.set(`Too many attempts. Try again in ${seconds} seconds.`);
      return;
    }
    this.formError.set("We couldn't create your account. Check your connection and try again.");
  }

  /** The message to show under a field, or null while it is valid or not visited yet. */
  protected errorFor(field: 'displayName' | 'email' | 'password'): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) return null;
    if (control.hasError('server')) return control.getError('server');
    const firstError = Object.keys(control.errors)[0];
    return MESSAGES[field][firstError] ?? null;
  }
}

const MESSAGES: Record<string, Record<string, string>> = {
  displayName: {
    required: 'Enter your name.',
    pattern: 'Enter your name.',
    maxlength: 'Use at most 100 characters.',
  },
  email: {
    required: 'Enter your email.',
    email: 'Enter a valid email.',
    maxlength: 'Use at most 254 characters.',
  },
  password: {
    required: 'Enter a password.',
    minlength: 'Use at least 10 characters.',
    maxBytes: 'This password is too long.',
  },
};

/** BCrypt uses only 72 bytes, so the backend limits passwords in bytes; emoji and accents count extra. */
function maxBytes(max: number): ValidatorFn {
  return (control) =>
    new TextEncoder().encode(control.value).length > max ? { maxBytes: true } : null;
}
