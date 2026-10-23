import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Body of POST /api/auth/register; the same fields as the backend's RegisterRequest. */
export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

/** Body of POST /api/auth/login; the same fields as the backend's LoginRequest. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Answer of POST /api/auth/login; the refresh token is not here, it arrives as an HttpOnly cookie. */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  /** Seconds until the access token expires. */
  expiresIn: number;
}

/** The signed-in account returned by GET /api/auth/me. */
export interface CurrentUser {
  id: string;
  email: string;
  displayName: string;
  role: 'USER' | 'ADMIN';
}

/** The only place that knows the backend's auth URLs. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  /** Requires the current access token; never returns password material. */
  me(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>('/api/auth/me');
  }

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

  /** 200 with an access token and a refresh cookie; 401 for a wrong email or password (same answer for both); 403 when the email is not verified. */
  login(body: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', body);
  }

  /** 200 with a new access token and a rotated refresh cookie; 400 when the cookie is missing, invalid, expired or reused; 409 when another tab refreshed less than 10 seconds ago. */
  refresh(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/refresh', null);
  }

  /** Always 204; the server revokes the session and clears the refresh cookie. */
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null);
  }
}
