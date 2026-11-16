import { Routes } from '@angular/router';
import { adminGuard, authGuard } from './core/auth-guard';

// loadComponent = lazy route: the page's code is downloaded only when someone visits it
// (like Spring's @Lazy, but at the browser/bundle level).
export const routes: Routes = [
  {
    path: 'holding',
    title: 'Loc is under change',
    loadComponent: () => import('./features/holding/holding-page').then((m) => m.HoldingPage),
  },
  {
    // The admin panel has its own layout instead of the member header and footer.
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin/admin-shell').then((m) => m.AdminShell),
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Overview – Admin – Loc',
        loadComponent: () => import('./features/admin/admin-overview').then((m) => m.AdminOverview),
      },
      { path: '**', redirectTo: '' },
    ],
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
      {
        path: 'me/settings',
        canActivate: [authGuard],
        loadComponent: () => import('./features/account/settings-page').then((m) => m.SettingsPage),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'profile' },
          {
            path: 'profile',
            title: 'Profile – Account settings – Loc',
            loadComponent: () =>
              import('./features/account/profile-settings').then((m) => m.ProfileSettings),
          },
          {
            path: 'security',
            title: 'Sign-in & security – Account settings – Loc',
            loadComponent: () =>
              import('./features/account/security-settings').then((m) => m.SecuritySettings),
          },
          {
            path: 'account',
            title: 'Account – Account settings – Loc',
            loadComponent: () =>
              import('./features/account/delete-account').then((m) => m.DeleteAccount),
          },
        ],
      },
      // Profile and account settings became sections of one settings page; keep old links working.
      { path: 'me/profile', redirectTo: 'me/settings/profile' },
      { path: 'me/account', redirectTo: 'me/settings/security' },
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
