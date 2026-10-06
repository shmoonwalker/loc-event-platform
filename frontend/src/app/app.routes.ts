import { Routes } from '@angular/router';

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
        loadComponent: () => import('./features/organizer/organizer-page').then((m) => m.OrganizerPage),
      },
      {
        path: '**',
        title: 'Page not found – Loc',
        loadComponent: () => import('./features/not-found/not-found-page').then((m) => m.NotFoundPage),
      },
    ],
  },
];
