import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { AccountApi } from '../../core/account-api';
import { AuthSession } from '../../core/auth-session';
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
  protected readonly session = inject(AuthSession);

  /** Checked first, so the current address is refused before any format message. */
  private readonly notCurrent = (control: AbstractControl<string>): ValidationErrors | null =>
    control.value.trim().toLowerCase() === this.session.currentUser()?.email.trim().toLowerCase()
      ? { sameEmail: true }
      : null;

  protected readonly form = this.fb.group({
    newEmail: [
      '',
      [this.notCurrent, Validators.required, Validators.email, Validators.maxLength(254)],
    ],
    currentPassword: ['', Validators.required],
  });

  /** form → sending → sent (show "check your new inbox"). */
  protected readonly status = signal<'form' | 'sending' | 'sent'>('form');
  protected readonly formError = signal<string | null>(null);
  protected readonly showPassword = signal(false);

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
        this.showPassword.set(false);
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
    sameEmail: 'This is already your email. Enter a different one.',
    required: 'Enter your new email.',
    email: 'Enter a valid email.',
    maxlength: 'Use at most 254 characters.',
  },
  currentPassword: { required: 'Enter your current password.' },
};
