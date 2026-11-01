import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { OrganizerInfo } from './models';

@Injectable({ providedIn: 'root' })
export class FollowedOrganizersApi {
  private readonly http = inject(HttpClient);

  /** Most recently followed first. Not paged. */
  list(): Observable<OrganizerInfo[]> {
    return this.http.get<OrganizerInfo[]>('/api/me/followed-organizers');
  }

  follow(id: string): Observable<void> {
    return this.http.put<void>(`/api/me/followed-organizers/${encodeURIComponent(id)}`, null);
  }

  unfollow(id: string): Observable<void> {
    return this.http.delete<void>(`/api/me/followed-organizers/${encodeURIComponent(id)}`);
  }
}
