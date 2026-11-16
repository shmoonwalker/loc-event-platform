import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountApi } from '../../core/account-api';
import { fieldError, showApiError } from './form-errors';

/** New address plus the current password; the change completes from the link sent to the new address. */
@Component({
  selector: 'app-change-email',
  imports: [ReactiveFormsModule],
  templateUrl: './change-email.html',
})
export class ChangeEmail {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(AccountApi);

  protected readonly form = this.fb.group({
    newEmail: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    currentPassword: ['', Validators.required],
  });

  /** form → sending → sent (show "check your new inbox"). */
  protected readonly status = signal<'form' | 'sending' | 'sent'>('form');
  protected readonly formError = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.status.set('sending');
    this.formError.set(null);
    const { currentPassword, newEmail } = this.form.getRawValue();
    this.api.changeEmail(currentPassword, newEmail).subscribe({
      next: () => {
        this.form.controls.currentPassword.reset();
        this.status.set('sent');
      },
      error: (err: HttpErrorResponse) => {
        this.status.set('form');
        this.formError.set(showApiError(this.form, err));
      },
    });
  }

  protected errorFor(field: 'newEmail' | 'currentPassword'): string | null {
    return fieldError(this.form.controls[field], MESSAGES[field]);
  }
}

const MESSAGES: Record<string, Record<string, string>> = {
  newEmail: {
    required: 'Enter your new email.',
    email: 'Enter a valid email.',
    maxlength: 'Use at most 254 characters.',
  },
  currentPassword: { required: 'Enter your current password.' },
};
