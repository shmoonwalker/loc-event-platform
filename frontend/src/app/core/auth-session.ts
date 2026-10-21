import { Injectable, computed, signal } from '@angular/core';

/**
 * The logged-in member's access token, kept in memory only: never in localStorage or sessionStorage,
 * where any script on the page could read it. A reload loses it; the refresh cookie brings it back.
 */
@Injectable({ providedIn: 'root' })
export class AuthSession {
  private readonly token = signal<string | null>(null);

  /** The current access token, or null when nobody is logged in. */
  readonly accessToken = this.token.asReadonly();

  readonly loggedIn = computed(() => this.token() !== null);

  /** Called after a successful login with the token from the response. */
  start(accessToken: string): void {
    this.token.set(accessToken);
  }

  end(): void {
    this.token.set(null);
  }
}
