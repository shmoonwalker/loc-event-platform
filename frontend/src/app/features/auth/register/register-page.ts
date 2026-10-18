import { Component, inject, signal } from '@angular/core';
import {
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../../core/auth-api';

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

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.status.set('sending');
    this.authApi.register(this.form.getRawValue()).subscribe({
      next: () => {
        // The issue asks that a password is never shown after submit.
        this.form.controls.password.reset();
        this.showPassword.set(false);
        this.status.set('sent');
      },
      error: () => this.status.set('form'),
    });
  }

  /** The message to show under a field, or null while it is valid or not visited yet. */
  protected errorFor(field: 'displayName' | 'email' | 'password'): string | null {
    const control = this.form.controls[field];
    if (!control.touched || !control.errors) return null;
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
