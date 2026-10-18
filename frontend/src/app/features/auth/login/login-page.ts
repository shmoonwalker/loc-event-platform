import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Log-in page: email and password. */
@Component({
  selector: 'app-login-page',
  imports: [RouterLink],
  template: `
    <section class="wrap py-16">
      <h1 class="display text-[clamp(36px,5vw,60px)]">Log in</h1>
      <p class="mt-4 text-muted">
        No account yet? <a routerLink="/register" class="link">Sign up</a>
      </p>
    </section>
  `,
})
export class LoginPage {}
