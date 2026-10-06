import { provideHttpClient, withFetch } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';

// The application-wide "bean configuration": providers registered here are available everywhere.
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch()),
    // anchorScrolling: a link with a #fragment (e.g. "See all" -> #all) scrolls to that element.
    // componentInputBinding: a routed page receives :id and ?page= as signal inputs (like @PathVariable / @RequestParam).
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' }), withComponentInputBinding()),
  ],
};
