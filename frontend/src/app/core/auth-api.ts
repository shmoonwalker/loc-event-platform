import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Body of POST /api/auth/register; the same fields as the backend's RegisterRequest. */
export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

/** The only place that knows the backend's auth URLs. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  /** Always answers 202 with no body, also for an email that already has an account. */
  register(body: RegisterRequest): Observable<void> {
    return this.http.post<void>('/api/auth/register', body);
  }

  /** 204 when verified; 400 for an unknown, expired or used token. */
  verifyEmail(token: string): Observable<void> {
    return this.http.post<void>('/api/auth/verify-email', { token });
  }

  /** Always 202, so the answer never reveals whether the email has an account. */
  resendVerification(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/resend-verification', { email });
  }
}
