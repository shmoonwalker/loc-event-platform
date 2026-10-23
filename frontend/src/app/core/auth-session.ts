import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import {
  Observable,
  Subject,
  catchError,
  finalize,
  map,
  share,
  switchMap,
  takeUntil,
  tap,
  throwError,
} from 'rxjs';
import { AuthApi, CurrentUser } from './auth-api';

/**
 * The logged-in member's access token, kept in memory only: never in localStorage or sessionStorage,
 * where any script on the page could read it. A reload loses it; the refresh cookie brings it back.
 */
@Injectable({ providedIn: 'root' })
export class AuthSession {
  private readonly api = inject(AuthApi);
  private readonly token = signal<string | null>(null);
  private readonly user = signal<CurrentUser | null>(null);
  private readonly accountChanged = new Subject<void>();
  private readonly ended = new Subject<void>();

  /** The current access token, or null when nobody is logged in. */
  readonly accessToken = this.token.asReadonly();
  readonly currentUser = this.user.asReadonly();
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');

  readonly loggedIn = computed(() => this.token() !== null);

  /** The refresh call in flight, so parallel callers share one request instead of each rotating the cookie. */
  private refreshing: Observable<string> | null = null;

  /** Subscribe after any sign-in path to load the account before opening the app. */
  start(accessToken: string): Observable<CurrentUser> {
    this.accountChanged.next();
    this.user.set(null);
    this.token.set(accessToken);
    return this.api
      .me()
      .pipe(
        takeUntil(this.accountChanged),
        tap({ next: (user) => this.user.set(user), error: () => this.clear() }),
      );
  }

  end(): void {
    this.accountChanged.next();
    this.ended.next();
    this.clear();
  }

  private clear(): void {
    this.token.set(null);
    this.user.set(null);
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
      switchMap((response) =>
        this.start(response.accessToken).pipe(map(() => response.accessToken)),
      ),
      tap({ error: () => this.clear() }),
      takeUntil(this.ended),
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
