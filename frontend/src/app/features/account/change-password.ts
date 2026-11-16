import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountApi } from '../../core/account-api';
import { newPasswordValidators } from '../../core/password-rules';
import { fieldError, showApiError } from './form-errors';

/** Current password plus a new one with the sign-up rules; other devices are signed out. */
@Component({
  selector: 'app-change-password',
  imports: [ReactiveFormsModule],
  templateUrl: './change-password.html',
})
export class ChangePassword {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(AccountApi);

  protected readonly form = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', newPasswordValidators],
  });

  protected readonly status = signal<'form' | 'sending' | 'saved'>('form');
  protected readonly formError = signal<string | null>(null);
  protected readonly showCurrent = signal(false);
  protected readonly showNew = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.status.set('sending');
    this.formError.set(null);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.api.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.form.reset();
        this.showCurrent.set(false);
        this.showNew.set(false);
        this.status.set('saved');
      },
      error: (err: HttpErrorResponse) => {
        this.status.set('form');
        this.formError.set(
          showApiError(this.form, err, { newPasswordWithinByteLimit: 'newPassword' }),
        );
      },
    });
  }

  protected errorFor(field: 'currentPassword' | 'newPassword'): string | null {
    return fieldError(this.form.controls[field], MESSAGES[field]);
  }
}

const MESSAGES: Record<string, Record<string, string>> = {
  currentPassword: { required: 'Enter your current password.' },
  newPassword: {
    required: 'Enter a new password.',
    minlength: 'Use at least 10 characters.',
    maxBytes: 'This password is too long.',
  },
};
