import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSession } from './auth-session';

/**
 * Lets signed-in members open the page; everyone else goes to the login page.
 * Only hides pages: the backend still checks the token on every API call.
 */
export const authGuard: CanActivateFn = (_route, state) =>
  inject(AuthSession).loggedIn() ||
  inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });

/** Admin pages use the loaded account's role; the backend must still authorize every action. */
export const adminGuard: CanActivateFn = () => {
  const session = inject(AuthSession);
  return session.isAdmin() || inject(Router).createUrlTree([session.loggedIn() ? '/' : '/login']);
};
