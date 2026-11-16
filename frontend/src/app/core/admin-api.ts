import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ReportReason } from './reports-api';

/** Open reports of one event or comment. */
export interface ReportGroup {
  targetType: 'EVENT' | 'COMMENT';
  /** The show id for an event, else the comment id. */
  targetId: string;
  openCount: number;
  /** Distinct, alphabetical. */
  reasons: ReportReason[];
  /** Oldest first. */
  reportIds: string[];
}

export interface HiddenEvent {
  eventId: string;
  /** Null after that admin's account was deleted. */
  hiddenBy: string | null;
  hiddenByName: string | null;
  hiddenAt: string;
  reason: string;
}

/** The admin API; the backend answers 403 to anyone who is not an admin. */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  /** Newest activity first. */
  reports(): Observable<ReportGroup[]> {
    return this.http.get<ReportGroup[]>('/api/admin/reports');
  }

  dismissReport(reportId: string): Observable<void> {
    return this.http.post<void>(`/api/admin/reports/${encodeURIComponent(reportId)}/dismiss`, null);
  }

  /** Also closes the event's open reports. */
  hideEvent(eventId: string, reason: string): Observable<void> {
    return this.http.post<void>(`/api/admin/events/${encodeURIComponent(eventId)}/hide`, {
      reason,
    });
  }

  unhideEvent(eventId: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/events/${encodeURIComponent(eventId)}/hide`);
  }

  /** Most recently hidden first. */
  hiddenEvents(): Observable<HiddenEvent[]> {
    return this.http.get<HiddenEvent[]>('/api/admin/hidden-events');
  }
}
