import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AccountApi } from '../../core/account-api';
import { AuthSession } from '../../core/auth-session';
import { fieldError, showApiError } from './form-errors';

/** Explains what goes, asks for the password and a second click, then signs out and opens the home page. */
@Component({
  selector: 'app-delete-account',
  imports: [ReactiveFormsModule],
  templateUrl: './delete-account.html',
})
export class DeleteAccount {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(AccountApi);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);

  protected readonly form = this.fb.group({
    currentPassword: ['', Validators.required],
  });

  /** form → confirm (ask once more) → sending; a failure goes back to form. */
  protected readonly status = signal<'form' | 'confirm' | 'sending'>('form');
  protected readonly formError = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.formError.set(null);
    if (this.status() === 'form') {
      this.status.set('confirm');
      return;
    }
    this.status.set('sending');
    this.api.deleteAccount(this.form.getRawValue().currentPassword).subscribe({
      next: () => {
        // The server already revoked the session and cleared the cookie; only the UI side is left.
        this.session.end();
        void this.router.navigateByUrl('/');
      },
      error: (err: HttpErrorResponse) => {
        this.status.set('form');
        this.formError.set(showApiError(this.form, err));
      },
    });
  }

  protected errorFor(): string | null {
    return fieldError(this.form.controls.currentPassword, {
      required: 'Enter your current password.',
    });
  }
}
