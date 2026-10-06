import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** "This does not exist" plus a way home. Calm on purpose: unlike ErrorState there is nothing to retry. */
@Component({
  selector: 'app-not-found-state',
  imports: [RouterLink],
  template: `
    <section class="wrap py-24 text-center">
      <h1 class="display text-5xl">{{ heading() }}</h1>
      <p class="mt-4 text-muted">{{ message() }}</p>
      <a routerLink="/" class="btn-dark mt-8">Back to events</a>
    </section>
  `,
})
export class NotFoundState {
  readonly heading = input.required<string>();
  readonly message = input.required<string>();
}
