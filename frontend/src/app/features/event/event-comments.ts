import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSession } from '../../core/auth-session';
import { CommentsApi } from '../../core/comments-api';
import { valueOf } from '../../core/resource';
import { formatWhen } from '../../core/time';
import { ErrorState } from '../../shared/error-state/error-state';
import { Pagination } from '../../shared/pagination/pagination';
import { CommentForm } from './comment-form';

/**
 * The event's comments, newest first, each with its replies. Anyone can read them; members write,
 * reply to top-level comments, and edit or delete their own. After every change the page is fetched again,
 * so what shows is what the API stored.
 */
@Component({
  selector: 'app-event-comments',
  imports: [NgTemplateOutlet, RouterLink, ErrorState, Pagination, CommentForm],
  templateUrl: './event-comments.html',
})
export class EventComments {
  private readonly api = inject(CommentsApi);
  protected readonly session = inject(AuthSession);
  protected readonly router = inject(Router);

  readonly eventId = input.required<string>();

  /** Zero-based; back to the first page when another event opens. */
  protected readonly page = linkedSignal({ source: this.eventId, computation: () => 0 });
  // Signing in or out refetches, so `mine` (and with it Edit and Delete) follows the viewer.
  protected readonly comments = rxResource({
    params: () => ({ id: this.eventId(), page: this.page(), signedIn: this.session.loggedIn() }),
    stream: ({ params }) => this.api.list(params.id, params.page),
  });
  protected readonly result = computed(() => valueOf(this.comments));

  /** The one comment with an open reply form, edit form or delete question. */
  protected readonly replyingTo = signal<string | null>(null);
  protected readonly editing = signal<string | null>(null);
  protected readonly confirmingDelete = signal<string | null>(null);
  protected readonly deleting = signal(false);
  protected readonly deleteError = signal<string | null>(null);

  protected readonly post = (body: string) => this.api.create(this.eventId(), body);
  protected readonly sendReply = (body: string) => this.api.reply(this.replyingTo()!, body);
  protected readonly sendEdit = (body: string) => this.api.edit(this.editing()!, body);

  protected readonly when = (iso: string) => formatWhen(iso, iso);

  /** A new comment is newest, so it shows on the first page. */
  protected posted(): void {
    if (this.page() === 0) this.comments.reload();
    else this.page.set(0);
  }

  protected changed(): void {
    this.closeForms();
    this.comments.reload();
  }

  protected openReply(id: string): void {
    this.closeForms();
    this.replyingTo.set(id);
  }

  protected openEdit(id: string): void {
    this.closeForms();
    this.editing.set(id);
  }

  protected askDelete(id: string): void {
    this.closeForms();
    this.confirmingDelete.set(id);
  }

  protected closeForms(): void {
    this.replyingTo.set(null);
    this.editing.set(null);
    this.confirmingDelete.set(null);
    this.deleteError.set(null);
  }

  protected delete(id: string): void {
    if (this.deleting()) return;
    this.deleting.set(true);
    this.deleteError.set(null);
    this.api
      .delete(id)
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: () => this.changed(),
        error: () =>
          this.deleteError.set(
            "We couldn't delete your comment. Check your connection and try again.",
          ),
      });
  }
}
