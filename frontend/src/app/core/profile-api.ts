import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AvatarPreset, CurrentUser } from './auth-api';

/** The signed-in member's display name and avatar. Every call answers with the updated account. */
@Injectable({ providedIn: 'root' })
export class ProfileApi {
  private readonly http = inject(HttpClient);

  /** 400 with field errors for a blank or too long name. */
  changeDisplayName(displayName: string): Observable<CurrentUser> {
    return this.http.patch<CurrentUser>('/api/me/profile', { displayName });
  }

  /** Also removes an uploaded picture, so the preset shows. */
  choosePreset(preset: AvatarPreset): Observable<CurrentUser> {
    return this.http.put<CurrentUser>('/api/me/avatar/preset', { preset });
  }

  /** 400 when the file is not a JPEG, PNG or WebP image, 413 above 5 MB, 429 after too many uploads, 503 when uploads are off. */
  uploadAvatar(file: File): Observable<CurrentUser> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<CurrentUser>('/api/me/avatar', body);
  }

  /** Back to the preset; without an upload nothing changes. */
  removeAvatar(): Observable<CurrentUser> {
    return this.http.delete<CurrentUser>('/api/me/avatar');
  }
}
