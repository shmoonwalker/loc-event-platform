import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CommentPage, EventComment } from './models';

@Injectable({ providedIn: 'root' })
export class CommentsApi {
  private readonly http = inject(HttpClient);

  /** Public; with a token, mine marks the viewer's own comments. */
  list(eventId: string, page: number): Observable<CommentPage> {
    return this.http.get<CommentPage>(`/api/events/${encodeURIComponent(eventId)}/comments`, {
      params: { page },
    });
  }

  create(eventId: string, body: string): Observable<EventComment> {
    return this.http.post<EventComment>(`/api/events/${encodeURIComponent(eventId)}/comments`, {
      body,
    });
  }

  /** Only top-level comments take replies. */
  reply(commentId: string, body: string): Observable<EventComment> {
    return this.http.post<EventComment>(`/api/comments/${encodeURIComponent(commentId)}/replies`, {
      body,
    });
  }

  edit(commentId: string, body: string): Observable<EventComment> {
    return this.http.patch<EventComment>(`/api/me/comments/${encodeURIComponent(commentId)}`, {
      body,
    });
  }

  delete(commentId: string): Observable<void> {
    return this.http.delete<void>(`/api/me/comments/${encodeURIComponent(commentId)}`);
  }
}
