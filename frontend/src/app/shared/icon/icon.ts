import { Component, input } from '@angular/core';

export type IconName = 'chevron' | 'check' | 'x' | 'sun' | 'moon' | 'photo' | 'screen' | 'pin' | 'search' | 'cloud' | 'rain' | 'snow' | 'storm' | 'fog';

/** One inline-SVG icon component instead of copy-pasting SVG markup. Decorative: hidden from screen readers. */
@Component({
  selector: 'app-icon',
  host: { class: 'inline-flex shrink-0' },
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @switch (name()) {
        @case ('chevron') { <path d="M6 9l6 6 6-6" /> }
        @case ('check') { <path d="M5 12l5 5L20 7" /> }
        @case ('x') { <path d="M6 6l12 12M18 6L6 18" /> }
        @case ('search') { <circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /> }
        @case ('pin') { <path d="M12 22s7-6.2 7-12a7 7 0 1 0-14 0c0 5.8 7 12 7 12z" /><circle cx="12" cy="10" r="2.5" /> }
        @case ('sun') {
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
        }
        @case ('moon') { <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" /> }
        @case ('photo') { <rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="9" cy="10" r="2" /><path d="M21 17l-5-5-9 8" /> }
        @case ('screen') { <rect x="3" y="4" width="18" height="12" rx="2" /><path d="M8 20h8M12 16v4" /> }
        @case ('cloud') { <path d="M18 10h-1.3A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z" /> }
        @case ('rain') { <path d="M20 16.6A5 5 0 0 0 18 7h-1.3A8 8 0 1 0 4 15.3" /><path d="M16 13v8M8 13v8M12 15v8" /> }
        @case ('snow') { <path d="M20 17.6A5 5 0 0 0 18 8h-1.3A8 8 0 1 0 4 16.3" /><path d="M8 16h.01M8 20h.01M12 18h.01M12 22h.01M16 16h.01M16 20h.01" /> }
        @case ('storm') { <path d="M19 16.9A5 5 0 0 0 18 7h-1.3a8 8 0 1 0-11.6 9" /><path d="M13 11l-4 6h6l-4 6" /> }
        @case ('fog') { <path d="M4 8h16M3 12h18M5 16h14M8 20h8" /> }
      }
    </svg>
  `,
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(18);
}
