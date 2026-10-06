import { Component, input, output } from '@angular/core';

/** A message plus one action button. role="alert" makes screen readers announce it when it appears. */
@Component({
  selector: 'app-error-state',
  template: `
    <div role="alert" class="rounded-[18px] border border-dashed border-line px-6 py-10 text-center">
      <p class="font-semibold">{{ message() }}</p>
      <button type="button" class="btn-dark mt-4" (click)="action.emit()">{{ actionLabel() }}</button>
    </div>
  `,
})
export class ErrorState {
  readonly message = input.required<string>();
  readonly actionLabel = input('Try again');
  readonly action = output<void>();
}
