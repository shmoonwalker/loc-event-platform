import { Component } from '@angular/core';
import { NotFoundState } from '../../shared/not-found-state/not-found-state';

/** Catch-all for URLs the router does not know. */
@Component({
  selector: 'app-not-found-page',
  imports: [NotFoundState],
  template: `<app-not-found-state
    heading="Page not found"
    message="That page does not exist (yet)."
  />`,
})
export class NotFoundPage {}
