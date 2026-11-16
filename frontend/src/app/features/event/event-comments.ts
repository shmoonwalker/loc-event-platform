import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AdminApi } from '../../core/admin-api';
import { AuthSession } from '../../core/auth-session';
import { CommentsApi } from '../../core/comments-api';
import { valueOf } from '../../core/resource';
import { formatWhen } from '../../core/time';
import { ErrorState } from '../../shared/error-state/error-state';
import { Pagination } from '../../shared/pagination/pagination';
import { ReportButton } from '../../shared/report-button/report-button';
import { CommentForm } from './comment-form';

/**
 * The event's comments, newest first, each with its replies. Anyone can read them; members write,
 * reply to top-level comments, and edit or delete their own. The admin account does not comment as a
 * member: it replies officially and can remove any comment with a reason. After every change the page is
 * fetched again, so what shows is what the API stored.
 */
@Component({
  selector: 'app-event-comments',
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    RouterLink,
    ErrorState,
    Pagination,
    CommentForm,
    ReportButton,
  ],
  templateUrl: './event-comments.html',
})
export class EventComments {
  private readonly api = inject(CommentsApi);
  private readonly admin = inject(AdminApi);
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
  /** Signed in and not the admin account, which does not comment as a member. */
  protected readonly member = computed(() => this.session.loggedIn() && !this.session.isAdmin());

  /** The one comment with an open reply form, edit form or delete question. */
  protected readonly replyingTo = signal<string | null>(null);
  protected readonly editing = signal<string | null>(null);
  protected readonly confirmingDelete = signal<string | null>(null);
  protected readonly deleting = signal(false);
  protected readonly deleteError = signal<string | null>(null);

  /** Admin only: the comment with an open Remove form, and its required reason (backend limits). */
  protected readonly removing = signal<string | null>(null);
  protected readonly removeError = signal<string | null>(null);
  protected readonly removeReason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(1000)],
  });

  protected readonly post = (body: string) => this.api.create(this.eventId(), body);
  protected readonly sendReply = (body: string) =>
    this.session.isAdmin()
      ? this.admin.officialReply(this.replyingTo()!, body)
      : this.api.reply(this.replyingTo()!, body);
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

  protected askRemove(id: string): void {
    this.closeForms();
    this.removeReason.reset();
    this.removing.set(id);
  }

  protected closeForms(): void {
    this.replyingTo.set(null);
    this.editing.set(null);
    this.confirmingDelete.set(null);
    this.deleteError.set(null);
    this.removing.set(null);
    this.removeError.set(null);
  }

  protected reasonError(): string | null {
    if (!this.removeReason.touched || !this.removeReason.errors) return null;
    return this.removeReason.hasError('maxlength')
      ? 'Use at most 1000 characters.'
      : 'Say why the comment is removed.';
  }

  protected remove(id: string): void {
    if (this.removeReason.invalid) {
      this.removeReason.markAsTouched();
      return;
    }
    if (this.deleting()) return;
    this.deleting.set(true);
    this.removeError.set(null);
    this.admin
      .removeComment(id, this.removeReason.value.trim())
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: () => this.changed(),
        error: (err: HttpErrorResponse) =>
          this.removeError.set(
            err.status === 400
              ? (err.error?.errors?.reason ??
                  err.error?.detail ??
                  'Check the reason and try again.')
              : err.status === 404
                ? 'This comment is no longer available.'
                : "We couldn't remove this comment. Check your connection and try again.",
          ),
      });
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
