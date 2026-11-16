import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
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

/** True when the page, or a route around it, is guarded for signed-in members or admins. */
export const needsAccount = (route: ActivatedRouteSnapshot | null): boolean =>
  !!route &&
  (!!route.routeConfig?.canActivate?.some((g) => g === authGuard || g === adminGuard) ||
    needsAccount(route.firstChild));
