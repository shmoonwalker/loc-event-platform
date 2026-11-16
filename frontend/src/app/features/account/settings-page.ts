import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Account settings of the signed-in member: the section list on the left, the chosen section on the right
 * (stacked on small screens). Each section has its own URL under /me/settings, so links and Back work.
 */
@Component({
  selector: 'app-settings-page',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './settings-page.html',
})
export class SettingsPage {
  protected readonly sections = [
    { path: 'profile', label: 'Profile' },
    { path: 'security', label: 'Sign-in & security' },
    { path: 'account', label: 'Account' },
  ];
}
