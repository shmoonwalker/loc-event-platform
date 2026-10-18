import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../../core/auth-api';

/** Opened from the verification email; posts the link's token so the account becomes verified. */
@Component({
  selector: 'app-verify-email-page',
  imports: [RouterLink],
  templateUrl: './verify-email-page.html',
})
export class VerifyEmailPage implements OnInit {
  private readonly authApi = inject(AuthApi);

  /** The ?token= query parameter from the email link. */
  readonly token = input<string>();

  protected readonly status = signal<'verifying' | 'verified' | 'invalid' | 'failed'>('verifying');

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
}
