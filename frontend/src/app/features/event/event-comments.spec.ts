import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthSession } from '../../core/auth-session';
import { CommentPage, EventComment } from '../../core/models';
import { EventComments } from './event-comments';

const comment = (over: Partial<EventComment>): EventComment => ({
  id: 'c1',
  author: { displayName: 'Anouk', avatarUrl: '/avatars/sun.svg' },
  body: 'Is there parking?',
  official: false,
  deleted: false,
  mine: false,
  createdAt: '2026-10-01T10:00:00Z',
  editedAt: null,
  replies: [],
  ...over,
});

const pageOf = (items: EventComment[], over: Partial<CommentPage> = {}): CommentPage => ({
  page: 0,
  size: 20,
  totalElements: items.length,
  totalPages: items.length ? 1 : 0,
  items,
  ...over,
});

const thread = pageOf([
  comment({
    id: 'c1',
    mine: true,
    editedAt: '2026-10-01T11:00:00Z',
    replies: [
      comment({
        id: 'r1',
        author: { displayName: 'Loc team', avatarUrl: 'https://images.example/loc.png' },
        body: 'Yes, behind the venue',
        official: true,
      }),
    ],
  }),
  comment({
    id: 'c2',
    author: null,
    body: null,
    deleted: true,
    replies: [comment({ id: 'r2', author: { displayName: 'Deleted user', avatarUrl: null } })],
  }),
]);

const LIST = '/api/events/e1/comments?page=0';

/** true signs in a member whose account is not loaded; 'ADMIN' signs in an admin. */
async function render(signedIn: boolean | 'ADMIN', list: CommentPage = thread) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  const http = TestBed.inject(HttpTestingController);
  if (signedIn === 'ADMIN') {
    TestBed.inject(AuthSession).start('token').subscribe();
    http.expectOne('/api/auth/me').flush({ id: 'a1', displayName: 'Loc team', role: 'ADMIN' });
  } else if (signedIn) TestBed.inject(AuthSession).start('token');
  const fixture = TestBed.createComponent(EventComments);
  fixture.componentRef.setInput('eventId', 'e1');
  fixture.detectChanges();
  http.expectOne(LIST).flush(list);
  await fixture.whenStable();

  const el = fixture.nativeElement as HTMLElement;
  const settle = () => fixture.whenStable();
  // Runs the template and effects without waiting for open requests, which whenStable would.
  const tick = () => fixture.detectChanges();
  const button = (label: string, within: ParentNode = el) =>
    [...within.querySelectorAll('button')].find((b) => b.textContent?.trim() === label);
  const item = (text: string) =>
    [...el.querySelectorAll('article')].find((a) => a.textContent?.includes(text))!;
  const write = async (text: string, form = el.querySelector('form')!) => {
    const box = form.querySelector('textarea')!;
    box.value = text;
    box.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit'));
    tick();
  };
  return { el, http, settle, tick, button, item, write };
}

describe('EventComments', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows comments with their replies, authors, avatars and markers to everyone', async () => {
    const { el, item } = await render(false);

    expect(el.querySelector('h2')?.textContent).toContain('(2)');
    expect(item('Anouk').textContent).toContain('Is there parking?');
    expect(item('Anouk').textContent).toContain('Thu, 1 Oct 2026, 12:00');
    expect(item('Anouk').textContent).toContain('edited');
    expect(item('Anouk').querySelector('img')?.getAttribute('src')).toBe('/avatars/sun.svg');
    expect(item('Loc team').textContent).toContain('Official');
    expect(item('Loc team').querySelector('img')?.getAttribute('src')).toBe(
      'https://images.example/loc.png',
    );
    expect(item('Anouk').textContent).not.toContain('Official');
    expect(item('Comment deleted')).toBeTruthy();
    expect(item('Deleted user').querySelector('img')).toBeNull();
  });

  it('asks signed-out visitors to log in and offers no forms or actions', async () => {
    const { el, button } = await render(false);

    expect(el.querySelector('a[href^="/login"]')?.textContent).toContain('Log in');
    expect(el.querySelector('form')).toBeNull();
    expect(button('Reply')).toBeUndefined();
    expect(button('Edit')).toBeUndefined();
    expect(button('Report')).toBeUndefined();
  });

  it('says so when there are no comments yet', async () => {
    const { el } = await render(true, pageOf([]));
    expect(el.textContent).toContain('No comments yet.');
  });

  it('posts a trimmed comment and fetches the first page again', async () => {
    const { http, write, el, settle, tick } = await render(true);
    await write('  See you there!  ');

    const req = http.expectOne('/api/events/e1/comments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'See you there!' });
    req.flush(comment({ id: 'c3' }), { status: 201, statusText: 'Created' });
    tick();
    http.expectOne(LIST).flush(thread);
    await settle();
    expect(el.querySelector('textarea')?.value).toBe('');
  });

  it('refuses empty and too long text without calling the API', async () => {
    const { write, el } = await render(true);
    await write('   ');
    expect(el.textContent).toContain('Write something first.');
    await write('a'.repeat(2001));
    expect(el.textContent).toContain('Use at most 2000 characters.');
  });

  it('shows the API errors next to the form', async () => {
    const { http, write, el, settle } = await render(true);
    await write('Hello');
    http
      .expectOne('/api/events/e1/comments')
      .flush(
        { title: 'Too many requests' },
        { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '42' } },
      );
    await settle();
    expect(el.querySelector('form')?.textContent).toContain('Try again in 42 seconds.');

    await write('Hello');
    http.expectOne('/api/events/e1/comments').flush(
      {
        title: 'Validation failed',
        errors: { body: 'Comment must not exceed 2000 characters' },
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    expect(el.querySelector('form')?.textContent).toContain(
      'Comment must not exceed 2000 characters',
    );

    await write('Hello');
    http
      .expectOne('/api/events/e1/comments')
      .flush(
        { title: 'Bad Request', detail: "A reply can't be replied to." },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();
    expect(el.querySelector('form')?.textContent).toContain("A reply can't be replied to.");
  });

  it('replies only to top-level comments that are not deleted', async () => {
    const { el, http, button, item, write, settle, tick } = await render(true);
    expect(button('Reply', item('Loc team'))).toBeUndefined();
    expect(button('Reply', item('Comment deleted'))).toBeUndefined();

    button('Reply', item('Anouk'))!.click();
    await settle();
    const form = [...el.querySelectorAll('form')].find((f) =>
      f.textContent?.includes('Reply to Anouk'),
    )!;
    await write('Thanks!', form);

    const req = http.expectOne('/api/comments/c1/replies');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'Thanks!' });
    req.flush(comment({ id: 'r3' }), { status: 201, statusText: 'Created' });
    tick();
    http.expectOne(LIST).flush(thread);
  });

  it('offers edit and delete only on your own comments', async () => {
    const { button, item } = await render(true);
    expect(button('Edit', item('Anouk'))).toBeTruthy();
    expect(button('Delete', item('Anouk'))).toBeTruthy();
    expect(button('Edit', item('Loc team'))).toBeUndefined();
    expect(button('Delete', item('Deleted user'))).toBeUndefined();
  });

  it("offers Report on other members' comments, not on your own or deleted ones", async () => {
    const { button, item } = await render(true);
    expect(button('Report', item('Loc team'))).toBeTruthy();
    expect(button('Report', item('Deleted user'))).toBeTruthy();
    expect(button('Report', item('Anouk'))).toBeUndefined();
    expect(button('Report', item('Comment deleted'))).toBeUndefined();
  });

  it('offers no Report to an admin', async () => {
    const { button } = await render('ADMIN');
    expect(button('Report')).toBeUndefined();
  });

  it('gives members no admin actions', async () => {
    const { button } = await render(true);
    expect(button('Remove')).toBeUndefined();
    expect(button('Official reply')).toBeUndefined();
  });

  it('gives an admin Official reply on top-level comments and Remove on every comment', async () => {
    const { button, item } = await render('ADMIN');
    expect(button('Official reply', item('Anouk'))).toBeTruthy();
    expect(button('Official reply', item('Loc team'))).toBeUndefined();
    expect(button('Reply')).toBeUndefined();
    expect(button('Remove', item('Anouk'))).toBeTruthy();
    expect(button('Remove', item('Loc team'))).toBeTruthy();
    expect(button('Remove', item('Deleted user'))).toBeTruthy();
    expect(button('Remove', item('Comment deleted'))).toBeUndefined();
    expect(button('Delete')).toBeUndefined();
    expect(button('Edit', item('Anouk'))).toBeTruthy();
  });

  it('posts an official reply through the admin API', async () => {
    const { el, http, button, item, write, settle, tick } = await render('ADMIN');
    button('Official reply', item('Anouk'))!.click();
    await settle();
    const form = [...el.querySelectorAll('form')].find((f) =>
      f.textContent?.includes('Official reply to Anouk'),
    )!;
    expect(button('Post official reply', form)).toBeTruthy();
    await write(' Yes, behind the venue. ', form);

    const req = http.expectOne('/api/admin/comments/c1/official-replies');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'Yes, behind the venue.' });
    req.flush(comment({ id: 'r3', official: true }), { status: 201, statusText: 'Created' });
    tick();
    http.expectOne(LIST).flush(thread);
  });

  it('removes a comment only with a reason', async () => {
    const { el, http, button, item, settle, tick } = await render('ADMIN');
    button('Remove', item('Loc team'))!.click();
    await settle();
    const form = item('Loc team').querySelector('form')!;
    form.dispatchEvent(new Event('submit'));
    tick();
    expect(form.textContent).toContain('Say why the comment is removed.');
    http.expectNone((req) => req.url.startsWith('/api/admin/comments'));

    const box = el.querySelector<HTMLTextAreaElement>('#remove-reason')!;
    box.value = '  Spam  ';
    box.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/admin/comments/r1');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ reason: 'Spam' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    tick();
    http.expectOne(LIST).flush(thread);
  });

  it('shows a failed removal in the form and Cancel closes it', async () => {
    const { el, http, button, item, settle, tick } = await render('ADMIN');
    button('Remove', item('Anouk'))!.click();
    await settle();
    const box = el.querySelector<HTMLTextAreaElement>('#remove-reason')!;
    box.value = 'Spam';
    box.dispatchEvent(new Event('input'));
    item('Anouk').querySelector('form')!.dispatchEvent(new Event('submit'));

    http.expectOne('/api/admin/comments/c1').flush({}, { status: 404, statusText: 'Not Found' });
    tick();
    expect(item('Anouk').querySelector('[role="alert"]')?.textContent).toContain(
      'This comment is no longer available.',
    );

    button('Cancel', item('Anouk'))!.click();
    await settle();
    expect(item('Anouk').querySelector('form')).toBeNull();
  });

  it('edits your comment starting from its text', async () => {
    const { http, button, item, write, settle, tick } = await render(true);
    button('Edit', item('Anouk'))!.click();
    await settle();
    const form = item('Anouk').querySelector('form')!;
    expect(form.querySelector('textarea')?.value).toBe('Is there parking?');
    await write('Is there bike parking?', form);

    const req = http.expectOne('/api/me/comments/c1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ body: 'Is there bike parking?' });
    req.flush(comment({ body: 'Is there bike parking?' }));
    tick();
    http.expectOne(LIST).flush(thread);
  });

  it('deletes your comment only after you confirm', async () => {
    const { http, button, item, settle, tick } = await render(true);
    button('Delete', item('Anouk'))!.click();
    await settle();
    expect(item('Anouk').textContent).toContain('Delete this comment for everyone?');

    button('Keep it', item('Anouk'))!.click();
    await settle();
    expect(item('Anouk').textContent).not.toContain('Delete this comment for everyone?');

    button('Delete', item('Anouk'))!.click();
    await settle();
    button('Delete', item('Anouk'))!.click();
    const req = http.expectOne('/api/me/comments/c1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    tick();
    http.expectOne(LIST).flush(thread);
  });

  it('pages through the comments', async () => {
    const { el, http, settle, tick } = await render(
      false,
      pageOf([comment({})], { totalElements: 21, totalPages: 2 }),
    );
    el.querySelector<HTMLButtonElement>('[aria-label="Page 2"]')!.click();
    tick();
    http.expectOne('/api/events/e1/comments?page=1').flush(pageOf([], { page: 1 }));
    await settle();
    expect(el.textContent).toContain('No comments yet.');
  });

  it('shows a retryable error when the comments fail to load', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(EventComments);
    fixture.componentRef.setInput('eventId', 'e1');
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(LIST).flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')?.textContent).toContain("couldn't load the comments");

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    fixture.detectChanges();
    http.expectOne(LIST).flush(thread);
  });
});
