import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type PhotoStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** Same reasons as an event or comment report. */
export type PhotoReportReason = 'WRONG_INFORMATION' | 'SPAM' | 'INAPPROPRIATE' | 'OTHER';

/**
 * One photo as the API sends it. cardUrl and fullUrl are used as given:
 * a public file URL when the photo is approved and that is configured, otherwise the file route.
 */
export interface EventPhoto {
  id: string;
  eventId: string;
  eventTitle?: string;
  status: PhotoStatus;
  cardUrl: string;
  fullUrl: string;
  createdAt: string; // ISO instant, UTC
  uploader: { displayName: string };
}

/** Event photos. Lists are the photo JSON itself, newest first; nothing here builds a storage URL. */
@Injectable({ providedIn: 'root' })
export class PhotosApi {
  private readonly http = inject(HttpClient);

  /** Public. Approved photos only. */
  list(eventId: string): Observable<EventPhoto[]> {
    return this.http.get<EventPhoto[]>(`/api/events/${encodeURIComponent(eventId)}/photos`);
  }

  /** The signed-in member's photos of this event, every status. 401 when signed out. */
  mine(eventId: string): Observable<EventPhoto[]> {
    return this.http.get<EventPhoto[]>(`/api/me/events/${encodeURIComponent(eventId)}/photos`);
  }

  /** 400 for a file that is not a JPEG, PNG or WebP, 413 above 10 MB, 409 at 10 photos, 429 when uploading too fast. */
  upload(eventId: string, file: File): Observable<EventPhoto> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<EventPhoto>(`/api/events/${encodeURIComponent(eventId)}/photos`, body);
  }

  /** The owner only. 204. */
  delete(eventId: string, photoId: string): Observable<void> {
    return this.http.delete<void>(
      `/api/events/${encodeURIComponent(eventId)}/photos/${encodeURIComponent(photoId)}`,
    );
  }

  /** An approved photo that is not the caller's. */
  report(
    eventId: string,
    photoId: string,
    report: { reason: PhotoReportReason; details: string },
  ): Observable<void> {
    return this.http.post<void>(
      `/api/events/${encodeURIComponent(eventId)}/photos/${encodeURIComponent(photoId)}/reports`,
      report,
    );
  }
}
