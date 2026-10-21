import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthSession } from './auth-session';

// These work with the refresh cookie or a password, not the access token. Spring answers 401 to an
// expired Bearer token even on permitAll endpoints, so sending it here would break the refresh itself.
const WITHOUT_TOKEN = ['/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];

/**
 * Adds the in-memory access token to our own API calls. On a 401 it refreshes once and retries the call;
 * when the refresh fails, the session is over and the visitor goes to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(AuthSession);
  const router = inject(Router);
  const token = session.accessToken();
  // Only our own API gets the token: a call to any other site must never see it.
  if (!token || !req.url.startsWith('/api/') || WITHOUT_TOKEN.includes(req.url)) return next(req);

  return next(withToken(req, token)).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401) return throwError(() => err);
      return session.refresh().pipe(
        catchError(() => {
          void router.navigateByUrl('/login');
          return throwError(() => err);
        }),
        switchMap((newToken) => next(withToken(req, newToken))),
      );
    }),
  );
};

const withToken = (req: HttpRequest<unknown>, token: string) =>
  req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
