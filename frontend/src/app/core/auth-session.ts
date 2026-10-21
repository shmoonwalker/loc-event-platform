import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, share, tap, throwError } from 'rxjs';
import { AuthApi } from './auth-api';

/**
 * The logged-in member's access token, kept in memory only: never in localStorage or sessionStorage,
 * where any script on the page could read it. A reload loses it; the refresh cookie brings it back.
 */
@Injectable({ providedIn: 'root' })
export class AuthSession {
  private readonly api = inject(AuthApi);
  private readonly token = signal<string | null>(null);

  /** The current access token, or null when nobody is logged in. */
  readonly accessToken = this.token.asReadonly();

  readonly loggedIn = computed(() => this.token() !== null);

  /** The refresh call in flight, so parallel callers share one request instead of each rotating the cookie. */
  private refreshing: Observable<string> | null = null;

  /** Called after a successful login with the token from the response. */
  start(accessToken: string): void {
    this.token.set(accessToken);
  }

  end(): void {
    this.token.set(null);
  }

  /**
   * Gets a new access token through the refresh cookie and starts the session with it.
   * The one refresh used both on startup and after a 401. Fails (and ends the session) when the cookie is not valid.
   */
  refresh(): Observable<string> {
    this.refreshing ??= this.api.refresh().pipe(
      // 409: another tab rotated the cookie a moment ago; the browser already has the new one, so ask once more.
      catchError((err: HttpErrorResponse) =>
        err.status === 409 ? this.api.refresh() : throwError(() => err),
      ),
      map((response) => response.accessToken),
      tap({ next: (token) => this.start(token), error: () => this.end() }),
      finalize(() => (this.refreshing = null)),
      share(),
    );
    return this.refreshing;
  }

  /** Ends the session on the server and in the UI; the UI side also ends when the server cannot be reached. */
  logout(): Observable<void> {
    return this.api.logout().pipe(finalize(() => this.end()));
  }
}
