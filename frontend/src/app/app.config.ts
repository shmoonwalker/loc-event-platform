import { provideHttpClient, withFetch } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';
import { catchError, of } from 'rxjs';
import { AuthSession } from './core/auth-session';

// The application-wide "bean configuration": providers registered here are available everywhere.
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch()),

    // A reload loses the in-memory token: ask the refresh cookie for a new one before the first page opens.
    // A 400 just means "not logged in", so the app starts signed out without an error.
    provideAppInitializer(() =>
      inject(AuthSession)
        .refresh()
        .pipe(catchError(() => of(null))),
    ),

    // anchorScrolling: a link with a #fragment (e.g. "See all" -> #all) scrolls to that element.
    // componentInputBinding: a routed page receives :id and ?page= as signal inputs (like @PathVariable / @RequestParam).
    provideRouter(
      routes,
      withInMemoryScrolling({ anchorScrolling: 'enabled' }),
      withComponentInputBinding(),
    ),
  ],
};
