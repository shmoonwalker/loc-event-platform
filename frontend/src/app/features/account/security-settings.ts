import { Component } from '@angular/core';
import { ChangeEmail } from './change-email';
import { ChangePassword } from './change-password';

/** The "Sign-in & security" section of the account settings: the email and the password. */
@Component({
  selector: 'app-security-settings',
  imports: [ChangeEmail, ChangePassword],
  template: `
    <div class="flex flex-col gap-12">
      <app-change-email />
      <app-change-password />
    </div>
  `,
})
export class SecuritySettings {}
