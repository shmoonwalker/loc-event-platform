import { Routes } from '@angular/router';
import { authGuard } from './core/auth-guard';

// loadComponent = lazy route: the page's code is downloaded only when someone visits it
// (like Spring's @Lazy, but at the browser/bundle level).
export const routes: Routes = [
  {
    path: 'holding',
    title: 'Loc is under change',
    loadComponent: () => import('./features/holding/holding-page').then((m) => m.HoldingPage),
  },
  {
    // Layout route: renders Shell (header + footer) and puts the matching child inside it.
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Loc – events in the Netherlands',
        loadComponent: () => import('./features/home/home-page').then((m) => m.HomePage),
      },
      {
        // The page sets the real title (the event's) once it has loaded.
        path: 'events/:id',
        title: 'Event – Loc',
        loadComponent: () => import('./features/event/event-page').then((m) => m.EventPage),
      },
      {
        path: 'organizers/:id',
        title: 'Organizer – Loc',
        loadComponent: () =>
          import('./features/organizer/organizer-page').then((m) => m.OrganizerPage),
      },
      {
        path: 'me',
        title: 'My Loc – Loc',
        canActivate: [authGuard],
        loadComponent: () => import('./features/me/me-page').then((m) => m.MePage),
      },
      // The Saved page became the first tab of My Loc; keep old links working.
      { path: 'saved', redirectTo: 'me' },
      {
        path: 'register',
        title: 'Create account – Loc',
        loadComponent: () =>
          import('./features/auth/register/register-page').then((m) => m.RegisterPage),
      },
      {
        path: 'login',
        title: 'Log in – Loc',
        loadComponent: () => import('./features/auth/login/login-page').then((m) => m.LoginPage),
      },
      {
        path: 'verify-email',
        title: 'Verify email – Loc',
        loadComponent: () =>
          import('./features/auth/verify-email/verify-email-page').then((m) => m.VerifyEmailPage),
      },
      {
        path: '**',
        title: 'Page not found – Loc',
        loadComponent: () =>
          import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
      },
    ],
  },
];
