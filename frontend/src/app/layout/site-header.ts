import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EventsApi } from '../core/events-api';
import { HomeCity } from '../core/home-city';
import { valueOf } from '../core/resource';
import { ThemeService } from '../core/theme';
import { IconComponent } from '../shared/icon/icon';

/** Brand, "Your city" picker (drives the Near-you rail) and the theme switch. */
@Component({
  selector: 'app-site-header',
  imports: [RouterLink, IconComponent],
  templateUrl: './site-header.html',
})
export class SiteHeader {
  private readonly api = inject(EventsApi);
  protected readonly homeCity = inject(HomeCity);
  protected readonly theme = inject(ThemeService);

  // rxResource = "run this Observable, expose value/loading/error as signals". No params => runs once.
  private readonly cities = rxResource({ stream: () => this.api.cities() });

  /** The API list, plus the saved city if it is not in the list (so the select never shows a wrong city). */
  protected readonly options = computed(() => {
    const list = valueOf(this.cities) ?? [];
    const current = this.homeCity.slug();
    return list.some((c) => c.slug === current) ? list : [{ slug: current, name: titleCase(current) }, ...list];
  });
}

const titleCase = (slug: string) => slug.split('-').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
