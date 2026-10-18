import { Component } from '@angular/core';

import { RouterLink } from '@angular/router';

/** Sign-up page: email, password and display name. */
@Component({
  selector: 'app-register-page',
  imports: [RouterLink],
  template: `
    <section class="wrap py-16">
      <h1 class="display text-[clamp(36px,5vw,60px)]">Create account</h1>
      <p class="mt-4 text-muted">
        Already have an account? <a routerLink="/login" class="link">Log in</a>
      </p>
    </section>
  `,
})
export class RegisterPage {}
