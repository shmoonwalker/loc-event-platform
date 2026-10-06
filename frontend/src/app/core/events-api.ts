import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { City, EventPage, FilterOptions, HomeView } from './models';

/** Query string values; an array becomes a repeated parameter (?tag=a&tag=b). */
export type QueryParams = Record<string, string | string[]>;

/**
 * The only place that knows backend URLs. Every method returns a cold Observable:
 * nothing is sent until somebody subscribes (rxResource does that for us).
 * Like a Spring @FeignClient / RestClient interface.
 */
@Injectable({ providedIn: 'root' })
export class EventsApi {
  private readonly http = inject(HttpClient);

  home(city: string): Observable<HomeView> {
    return this.http.get<HomeView>('/api/home', { params: { city } });
  }

  events(params: QueryParams, size: number): Observable<EventPage> {
    return this.http.get<EventPage>('/api/events', { params: { ...params, size } });
  }

  cities(q = ''): Observable<City[]> {
    return this.http.get<City[]>('/api/cities', { params: q ? { q } : {} });
  }

  filterOptions(): Observable<FilterOptions> {
    return this.http.get<FilterOptions>('/api/events/filter-options');
  }
}
