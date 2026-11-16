import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Anyone can send feedback; with a token the backend links it to the member. */
@Injectable({ providedIn: 'root' })
export class FeedbackApi {
  private readonly http = inject(HttpClient);

  send(message: string, contactEmail: string | null): Observable<unknown> {
    return this.http.post('/api/feedback', { message, contactEmail });
  }
}
