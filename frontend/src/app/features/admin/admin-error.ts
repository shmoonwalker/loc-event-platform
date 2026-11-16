import { Component, input, output } from '@angular/core';

/** A failed load inside the admin panel, with a retry. role="alert" announces it. */
@Component({
  selector: 'app-admin-error',
  template: `
    <div role="alert" class="rounded-md border border-admin-line bg-admin-surface p-5">
      <p class="font-semibold">{{ message() }}</p>
      <button type="button" class="admin-btn-quiet mt-3" (click)="retry.emit()">Try again</button>
    </div>
  `,
})
export class AdminError {
  readonly message = input.required<string>();
  readonly retry = output<void>();
}
