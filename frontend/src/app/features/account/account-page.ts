import { Component } from '@angular/core';
import { ChangeEmail } from './change-email';
import { ChangePassword } from './change-password';

/** Account settings of the signed-in member: sign-in details and closing the account. */
@Component({
  selector: 'app-account-page',
  imports: [ChangePassword, ChangeEmail],
  templateUrl: './account-page.html',
})
export class AccountPage {}
