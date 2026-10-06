import { Injectable, effect, signal } from '@angular/core';

export type Theme = 'light' | 'dark';
const KEY = 'loc-theme';

/**
 * Light/dark theme as a signal. The effect mirrors it onto <html data-theme="...">, which is what the
 * CSS variables in styles.css react to. The initial value was already set by the inline script in
 * index.html (saved choice, else the OS preference), so we just read it back.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(document.documentElement.dataset['theme'] === 'dark' ? 'dark' : 'light');

  constructor() {
    effect(() => {
      document.documentElement.dataset['theme'] = this.theme();
    });
  }

  toggle(): void {
    this.theme.update((t) => (t === 'dark' ? 'light' : 'dark'));
    try {
      localStorage.setItem(KEY, this.theme()); // only a deliberate choice is remembered
    } catch {
      /* storage can be blocked (private mode); the theme still works for this visit */
    }
  }
}
