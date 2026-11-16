import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ReportGroup } from '../../core/admin-api';
import { AdminReports } from './admin-reports';

const EVENT_GROUP: ReportGroup = {
  targetType: 'EVENT',
  targetId: 'e1',
  openCount: 2,
  reasons: ['INAPPROPRIATE', 'SPAM'],
  reportIds: ['r1', 'r2'],
  eventId: 'e1',
  eventTitle: 'Jazz night',
  eventHidden: false,
  commentBody: null,
  commentAuthorName: null,
};
const COMMENT_GROUP: ReportGroup = {
  targetType: 'COMMENT',
  targetId: 'c1',
  openCount: 1,
  reasons: ['WRONG_INFORMATION'],
  reportIds: ['r3'],
  eventId: 'e2',
  eventTitle: 'Open air cinema',
  eventHidden: true,
  commentBody: 'Buy cheap tickets at example.com',
  commentAuthorName: 'Bob',
};
const LIST = '/api/admin/reports';

function setup() {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
}

async function render(list: ReportGroup[] = [EVENT_GROUP, COMMENT_GROUP]) {
  setup();
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AdminReports);
  fixture.detectChanges();
  http.expectOne(LIST).flush(list);
  await fixture.whenStable();

  const el = fixture.nativeElement as HTMLElement;
  const tick = () => fixture.detectChanges();
  const row = (text: string) =>
    [...el.querySelectorAll('li')].find((li) => li.textContent?.includes(text))!;
  const button = (label: string, within: ParentNode = el) =>
    [...within.querySelectorAll('button')].find((b) => b.textContent?.trim() === label);
  return { el, http, tick, row, button, settle: () => fixture.whenStable() };
}

describe('AdminReports', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows one row per reported item with its count and reasons', async () => {
    const { el, row } = await render();

    expect(el.querySelector('h1')?.textContent).toContain('Reports');
    expect(row('Jazz night').textContent).toContain('Event');
    expect(row('Jazz night').textContent).toContain('2 reports');
    expect(row('Jazz night').textContent).toContain('Inappropriate, Spam');
    expect(row('Jazz night').querySelector('a')?.getAttribute('href')).toBe('/events/e1');
    expect(row('cheap tickets').textContent).toContain('Comment');
    expect(row('cheap tickets').textContent).toContain('1 report');
    expect(row('cheap tickets').textContent).toContain('Wrong information');
  });

  it('shows the reported comment with its author and event, unlinked when the event is hidden', async () => {
    const { row } = await render();
    const comment = row('cheap tickets');

    expect(comment.textContent).toContain('“Buy cheap tickets at example.com”');
    expect(comment.textContent).toContain('by Bob on');
    expect(comment.textContent).toContain('Open air cinema');
    expect(comment.textContent).toContain('Hidden');
    expect(comment.querySelector('a')).toBeNull();
  });

  it('says when the comment was deleted, its author account is gone, or it no longer exists', async () => {
    const { row } = await render([
      { ...COMMENT_GROUP, commentBody: null, commentAuthorName: null, eventHidden: false },
      { ...COMMENT_GROUP, targetId: 'c9', eventId: null, eventTitle: null, commentBody: null },
    ]);

    expect(row('Comment deleted').textContent).toContain('by a deleted account on');
    expect(row('Comment deleted').querySelector('a')?.getAttribute('href')).toBe('/events/e2');
    expect(row('no longer exists').textContent).toContain('This comment no longer exists.');
  });

  it('shows the event id when the event is not in the catalog', async () => {
    const { row } = await render([{ ...EVENT_GROUP, eventTitle: null }]);
    expect(row('e1').textContent).toContain('Event e1');
    expect(row('e1').querySelector('a')).toBeNull();
  });

  it('offers Hide only for events', async () => {
    const { row, button } = await render();
    expect(button('Hide event', row('Jazz night'))).toBeDefined();
    expect(button('Hide event', row('cheap tickets'))).toBeUndefined();
    expect(button('Dismiss', row('cheap tickets'))).toBeDefined();
  });

  it('dismisses every open report of the item, then loads the list again', async () => {
    const { el, http, tick, row, button, settle } = await render();
    button('Dismiss', row('Jazz night'))!.click();

    const calls = http.match((req) => req.url.endsWith('/dismiss'));
    expect(calls.map((c) => [c.request.method, c.request.url])).toEqual([
      ['POST', '/api/admin/reports/r1/dismiss'],
      ['POST', '/api/admin/reports/r2/dismiss'],
    ]);
    calls.forEach((c) => c.flush(null, { status: 204, statusText: 'No Content' }));
    tick();
    http.expectOne(LIST).flush([COMMENT_GROUP]);
    await settle();

    expect(el.textContent).not.toContain('Jazz night');
  });

  it('treats a report that is already closed as done', async () => {
    const { http, tick, row, button, settle } = await render();
    button('Dismiss', row('cheap tickets'))!.click();

    http
      .expectOne('/api/admin/reports/r3/dismiss')
      .flush({}, { status: 404, statusText: 'Not Found' });
    tick();
    http.expectOne(LIST).flush([EVENT_GROUP]);
    await settle();
  });

  it('shows a failed dismiss on its row', async () => {
    const { http, tick, row, button } = await render();
    button('Dismiss', row('cheap tickets'))!.click();
    http.expectOne('/api/admin/reports/r3/dismiss').error(new ProgressEvent('error'));
    tick();

    expect(row('cheap tickets').querySelector('[role="alert"]')?.textContent).toContain(
      "We couldn't dismiss these reports.",
    );
  });

  it('hides an event with a required reason', async () => {
    const { el, http, tick, row, button, settle } = await render();
    button('Hide event', row('Jazz night'))!.click();
    tick();

    const form = row('Jazz night').querySelector('form')!;
    form.dispatchEvent(new Event('submit'));
    tick();
    expect(form.textContent).toContain('Say why the event is hidden.');
    http.expectNone((req) => req.url.endsWith('/hide'));

    const box = el.querySelector<HTMLTextAreaElement>('#hide-reason')!;
    box.value = '  Duplicate listing  ';
    box.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/admin/events/e1/hide');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Duplicate listing' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    tick();
    http.expectOne(LIST).flush([COMMENT_GROUP]);
    await settle();

    expect(el.querySelector('form')).toBeNull();
  });

  it('shows the API reason error under the hide form', async () => {
    const { el, http, tick, row, button } = await render();
    button('Hide event', row('Jazz night'))!.click();
    tick();
    const box = el.querySelector<HTMLTextAreaElement>('#hide-reason')!;
    box.value = 'Duplicate';
    box.dispatchEvent(new Event('input'));
    row('Jazz night').querySelector('form')!.dispatchEvent(new Event('submit'));

    http
      .expectOne('/api/admin/events/e1/hide')
      .flush(
        { errors: { reason: 'Reason must not exceed 1000 characters' } },
        { status: 400, statusText: 'Bad Request' },
      );
    tick();

    expect(row('Jazz night').querySelector('[role="alert"]')?.textContent).toContain(
      'Reason must not exceed 1000 characters',
    );
  });

  it('says when there are no open reports', async () => {
    const { el } = await render([]);
    expect(el.textContent).toContain('No open reports.');
  });

  it('shows a retryable error when the list fails to load', async () => {
    setup();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminReports);
    fixture.detectChanges();
    http.expectOne(LIST).error(new ProgressEvent('error'));
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      "We couldn't load the reports.",
    );
    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    fixture.detectChanges();
    http.expectOne(LIST).flush([]);
    await fixture.whenStable();
    expect(el.textContent).toContain('No open reports.');
  });
});
