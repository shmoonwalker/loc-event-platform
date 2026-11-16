import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Changes to the signed-in account. Each needs the current password: a wrong one answers 401
 * with the title "Invalid credentials", and all of them share five attempts per 15 minutes (429).
 */
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);

  /** 204; other devices are signed out and this browser gets a new refresh cookie. */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/me/password', { currentPassword, newPassword });
  }

  /** 202, also for an address that already has an account; the link goes to /verify-email. */
  changeEmail(currentPassword: string, newEmail: string): Observable<void> {
    return this.http.post<void>('/api/me/email', { currentPassword, newEmail });
  }

  /** 204; the server also clears the refresh cookie. */
  deleteAccount(currentPassword: string): Observable<void> {
    return this.http.delete<void>('/api/me', { body: { currentPassword } });
  }
}
