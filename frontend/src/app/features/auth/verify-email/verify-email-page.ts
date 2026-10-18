import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../../core/auth-api';

/** Opened from the verification email; posts the link's token so the account becomes verified. */
@Component({
  selector: 'app-verify-email-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './verify-email-page.html',
})
export class VerifyEmailPage implements OnInit {
  private readonly authApi = inject(AuthApi);
  private readonly fb = inject(NonNullableFormBuilder);

  /** The ?token= query parameter from the email link. */
  readonly token = input<string>();

  protected readonly status = signal<'verifying' | 'verified' | 'invalid' | 'failed'>('verifying');

  protected readonly resendForm = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
  });

  protected readonly resendStatus = signal<'idle' | 'sending' | 'sent'>('idle');
  protected readonly resendError = signal<string | null>(null);

  ngOnInit(): void {
    this.verify();
  }

  protected verify(): void {
    const token = this.token();
    if (!token) {
      this.status.set('invalid');
      return;
    }
    this.status.set('verifying');
    this.authApi.verifyEmail(token).subscribe({
      next: () => this.status.set('verified'),
      error: (err: HttpErrorResponse) => this.status.set(err.status === 400 ? 'invalid' : 'failed'),
    });
  }

  protected resend(): void {
    if (this.resendForm.invalid) {
      this.resendForm.markAllAsTouched();
      return;
    }
    this.resendStatus.set('sending');
    this.resendError.set(null);
    this.authApi.resendVerification(this.resendForm.getRawValue().email).subscribe({
      next: () => this.resendStatus.set('sent'),
      error: (err: HttpErrorResponse) => {
        this.resendStatus.set('idle');
        this.resendError.set(
          err.status === 429
            ? `Too many attempts. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`
            : "We couldn't send the link. Check your connection and try again.",
        );
      },
    });
  }
}
