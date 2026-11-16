import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { needsAccount } from '../core/auth-guard';
import { AuthSession } from '../core/auth-session';

/**
 * The signed-in member's avatar in the header; it opens a small menu with the account pages and logout.
 * A disclosure (button + list of links), not an ARIA menu: Tab moves through it like any links.
 * Escape and a click outside close it.
 */
@Component({
  selector: 'app-account-menu',
  imports: [RouterLink],
  templateUrl: './account-menu.html',
  host: {
    class: 'relative',
    '(document:click)': 'closeOnOutsideClick($event)',
    '(keydown.escape)': 'closeAndFocusButton()',
  },
})
export class AccountMenu {
  protected readonly session = inject(AuthSession);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly button = viewChild<ElementRef<HTMLButtonElement>>('menuButton');

  protected readonly open = signal(false);

  /**
   * Public pages work signed out, so the visitor stays where they are; a page for members only goes home.
   * Also when the server could not be reached: the UI is logged out anyway.
   */
  protected logout(): void {
    this.open.set(false);
    const leave = () => {
      if (needsAccount(this.router.routerState.snapshot.root)) void this.router.navigateByUrl('/');
    };
    this.session.logout().subscribe({ complete: leave, error: leave });
  }

  protected closeOnOutsideClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node | null)) this.open.set(false);
  }

  protected closeAndFocusButton(): void {
    if (!this.open()) return;
    this.open.set(false);
    this.button()?.nativeElement.focus();
  }
}
