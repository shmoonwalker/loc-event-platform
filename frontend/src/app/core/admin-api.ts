import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EventComment } from './models';
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

/** Website feedback; null fields are left out of the JSON. */
export interface Feedback {
  id: string;
  /** Absent for a visitor, or after that account was deleted. */
  userId?: string;
  message: string;
  contactEmail?: string;
  createdAt: string;
  handledAt?: string;
  handledBy?: string;
}

/** Open feedback first, then the newest. */
export interface FeedbackPage {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  items: Feedback[];
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

  /** Removes any comment or reply for everyone and closes its open reports. */
  removeComment(commentId: string, reason: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/comments/${encodeURIComponent(commentId)}`, {
      body: { reason },
    });
  }

  /** Only top-level comments take replies. */
  officialReply(commentId: string, body: string): Observable<EventComment> {
    return this.http.post<EventComment>(
      `/api/admin/comments/${encodeURIComponent(commentId)}/official-replies`,
      { body },
    );
  }

  feedback(page: number): Observable<FeedbackPage> {
    return this.http.get<FeedbackPage>('/api/admin/feedback', { params: { page } });
  }

  markFeedbackHandled(feedbackId: string): Observable<Feedback> {
    return this.http.post<Feedback>(
      `/api/admin/feedback/${encodeURIComponent(feedbackId)}/handled`,
      null,
    );
  }

  /** Most recently hidden first. */
  hiddenEvents(): Observable<HiddenEvent[]> {
    return this.http.get<HiddenEvent[]>('/api/admin/hidden-events');
  }
}
