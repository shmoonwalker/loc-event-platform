import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthSession } from '../../core/auth-session';
import { ThemeService } from '../../core/theme';
import { IconComponent } from '../../shared/icon/icon';
import { ADMIN_SECTIONS } from './admin-sections';

/**
 * The admin panel's own chrome, used instead of the member header and footer: a plum sidebar with the
 * sections (a top bar with a Sections button on small screens), and the chosen page next to it.
 */
@Component({
  selector: 'app-admin-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, IconComponent],
  templateUrl: './admin-shell.html',
  host: { class: 'admin block' },
})
export class AdminShell {
  protected readonly session = inject(AuthSession);
  protected readonly theme = inject(ThemeService);
  protected readonly sections = ADMIN_SECTIONS;

  /** Small screens only: the section list is folded away until the Sections button opens it. */
  protected readonly navOpen = signal(false);
}
