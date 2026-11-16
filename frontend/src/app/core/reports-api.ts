import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type ReportReason = 'WRONG_INFORMATION' | 'SPAM' | 'INAPPROPRIATE' | 'OTHER';
export type ReportTarget = 'event' | 'comment';

export interface ReportRequest {
  reason: ReportReason;
  /** Optional, at most 1000 characters. */
  details: string | null;
}

/** Members report an event or a comment. The answer never says what an admin later decided. */
@Injectable({ providedIn: 'root' })
export class ReportsApi {
  private readonly http = inject(HttpClient);

  report(target: ReportTarget, id: string, body: ReportRequest): Observable<unknown> {
    const path = target === 'event' ? 'events' : 'comments';
    return this.http.post(`/api/${path}/${encodeURIComponent(id)}/reports`, body);
  }
}
