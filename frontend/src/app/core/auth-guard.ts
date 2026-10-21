import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSession } from './auth-session';

/**
 * Lets signed-in members open the page; everyone else goes to the login page.
 * Only hides pages: the backend still checks the token on every API call.
 */
export const authGuard: CanActivateFn = () =>
  inject(AuthSession).loggedIn() || inject(Router).createUrlTree(['/login']);
