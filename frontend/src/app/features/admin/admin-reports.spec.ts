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
  eventTitle: 'Old market',
  commentBody: 'Parking is free here',
  commentAuthorName: 'Anouk',
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
  return { el, fixture, http, tick, row, button, settle: () => fixture.whenStable() };
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
    expect(row('Parking is free here').textContent).toContain('Comment');
    expect(row('Parking is free here').textContent).toContain('1 report');
    expect(row('Parking is free here').textContent).toContain('Wrong information');
    expect(row('Parking is free here').textContent).toContain('Anouk');
    expect(row('Parking is free here').querySelector('a')?.getAttribute('href')).toBe('/events/e2');
  });

  it('shows a removed comment and a deleted author without their text or name', async () => {
    const { row } = await render([
      { ...COMMENT_GROUP, commentBody: null, commentAuthorName: null },
    ]);
    expect(row('Comment removed').textContent).toContain('Deleted user');
  });

  it('shows the event id when it has no title', async () => {
    setup();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminReports);
    fixture.detectChanges();
    http.expectOne(LIST).flush([{ ...EVENT_GROUP, eventTitle: null }]);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('li')?.textContent).toContain('Event e1');
    expect(el.querySelector('li a')).toBeNull();
  });

  it('offers Hide only for events', async () => {
    const { row, button } = await render();
    expect(button('Hide event', row('Jazz night'))).toBeDefined();
    expect(button('Hide event', row('Parking is free here'))).toBeUndefined();
    expect(button('Dismiss', row('Parking is free here'))).toBeDefined();
  });

  it('shows a photo report and removes the photo', async () => {
    const { fixture, http, tick, row, button } = await render([
      {
        ...EVENT_GROUP,
        targetType: 'PHOTO',
        targetId: 'p1',
        eventId: 'e1',
        eventTitle: 'Jazz night',
        openCount: 1,
        reasons: ['INAPPROPRIATE'],
        reportIds: ['r9'],
      },
    ]);

    expect(row('Jazz night').textContent).toContain('Photo');
    expect(row('Jazz night').textContent).toContain('p1');
    expect(button('Hide event', row('Jazz night'))).toBeUndefined();
    expect(button('Remove photo', row('Jazz night'))).toBeDefined();
    button('Remove photo', row('Jazz night'))!.click();

    const req = http.expectOne('/api/admin/photos/p1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    tick();
    http.expectOne(LIST).flush([]);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('No open reports');
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
    button('Dismiss', row('Parking is free here'))!.click();

    http
      .expectOne('/api/admin/reports/r3/dismiss')
      .flush({}, { status: 404, statusText: 'Not Found' });
    tick();
    http.expectOne(LIST).flush([EVENT_GROUP]);
    await settle();
  });

  it('shows a failed dismiss on its row', async () => {
    const { http, tick, row, button } = await render();
    button('Dismiss', row('Parking is free here'))!.click();
    http.expectOne('/api/admin/reports/r3/dismiss').error(new ProgressEvent('error'));
    tick();

    expect(row('Parking is free here').querySelector('[role="alert"]')?.textContent).toContain(
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
