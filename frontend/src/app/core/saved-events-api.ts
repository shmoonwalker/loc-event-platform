import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EventDetail, SavedEventPage } from './models';

@Injectable({ providedIn: 'root' })
export class SavedEventsApi {
  private readonly http = inject(HttpClient);

  list(page: number): Observable<SavedEventPage> {
    return this.http.get<SavedEventPage>('/api/me/saved-events', { params: { page } });
  }

  /** Also works for a withdrawn or past event, as long as this member saved it. */
  get(id: string): Observable<EventDetail> {
    return this.http.get<EventDetail>(`/api/me/saved-events/${encodeURIComponent(id)}`);
  }

  save(id: string): Observable<void> {
    return this.http.put<void>(`/api/me/saved-events/${encodeURIComponent(id)}`, null);
  }

  unsave(id: string): Observable<void> {
    return this.http.delete<void>(`/api/me/saved-events/${encodeURIComponent(id)}`);
  }
}
