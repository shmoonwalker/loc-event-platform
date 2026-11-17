import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, finalize } from 'rxjs';
import { AdminApi, AdminPhoto, AdminPhotoStatus } from '../../core/admin-api';
import { valueOf } from '../../core/resource';
import { formatWhen } from '../../core/time';
import { AdminError } from './admin-error';

/**
 * Member photos of events. Pending photos are hidden from everyone else until an admin approves
 * them; approved photos can be removed. The list is fetched again after each action.
 */
@Component({
  selector: 'app-admin-photos',
  imports: [RouterLink, AdminError],
  templateUrl: './admin-photos.html',
})
export class AdminPhotos {
  private readonly api = inject(AdminApi);

  protected readonly status = signal<Exclude<AdminPhotoStatus, 'REJECTED'>>('PENDING');
  protected readonly photos = rxResource({
    params: () => this.status(),
    stream: ({ params }) => this.api.photos(params),
  });
  protected readonly list = computed(() => valueOf(this.photos));
  protected readonly when = (iso: string) => formatWhen(iso, iso);

  protected readonly busy = signal<string | null>(null);
  protected readonly confirming = signal<string | null>(null);
  protected readonly failed = signal<{ id: string; message: string } | null>(null);
  protected readonly viewing = signal<AdminPhoto | null>(null);

  protected show(status: 'PENDING' | 'APPROVED'): void {
    if (this.status() === status) return;
    this.failed.set(null);
    this.confirming.set(null);
    this.status.set(status);
  }

  protected view(photo: AdminPhoto, dialog: HTMLDialogElement): void {
    this.viewing.set(photo);
    if (!dialog.open) dialog.showModal();
  }

  protected approve(id: string): void {
    this.run(id, this.api.approvePhoto(id), "We couldn't approve this photo.");
  }

  protected reject(id: string): void {
    this.run(id, this.api.rejectPhoto(id), "We couldn't reject this photo.");
  }

  protected askRemove(id: string): void {
    this.failed.set(null);
    this.confirming.set(id);
  }

  protected remove(id: string): void {
    this.run(id, this.api.removePhoto(id), "We couldn't remove this photo.");
  }

  private run(id: string, action: Observable<unknown>, failure: string): void {
    if (this.busy()) return;
    this.busy.set(id);
    this.failed.set(null);
    action.pipe(finalize(() => this.busy.set(null))).subscribe({
      next: () => {
        this.confirming.set(null);
        this.photos.reload();
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 404) {
          this.confirming.set(null);
          this.photos.reload();
          return;
        }
        this.failed.set({
          id,
          message:
            err.status === 0 ? `${failure} Check your connection and try again.` : `${failure}`,
        });
      },
    });
  }
}
