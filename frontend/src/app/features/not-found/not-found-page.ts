import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Catch-all for URLs the router does not know (also where event links land until the detail page exists). */
@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  template: `
    <section class="wrap py-24 text-center">
      <h1 class="display text-5xl">Page not found</h1>
      <p class="mt-4 text-muted">That page does not exist (yet).</p>
      <a routerLink="/" class="btn-dark mt-8">Back to events</a>
    </section>
  `,
})
export class NotFoundPage {}
