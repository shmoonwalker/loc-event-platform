import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HiddenEvent } from '../../core/admin-api';
import { AdminHiddenEvents } from './admin-hidden-events';

const HIDDEN: HiddenEvent[] = [
  {
    eventId: 'e1',
    hiddenBy: 'a1',
    hiddenByName: 'Shadi',
    hiddenAt: '2026-11-16T20:00:00Z',
    reason: 'Duplicate listing',
    eventTitle: 'Jazz night',
  },
  {
    eventId: 'e2',
    hiddenBy: null,
    hiddenByName: null,
    hiddenAt: '2026-11-01T09:30:00Z',
    reason: 'Spam',
    eventTitle: null,
  },
];
const LIST = '/api/admin/hidden-events';

async function render(list: HiddenEvent[] | 'error' = HIDDEN) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AdminHiddenEvents);
  fixture.detectChanges();
  if (list === 'error') http.expectOne(LIST).error(new ProgressEvent('error'));
  else http.expectOne(LIST).flush(list);
  await fixture.whenStable();

  const el = fixture.nativeElement as HTMLElement;
  const row = (text: string) =>
    [...el.querySelectorAll('li')].find((li) => li.textContent?.includes(text))!;
  const unhide = (text: string) =>
    [...row(text).querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Unhide')!;
  return { fixture, el, http, row, unhide };
}

describe('AdminHiddenEvents', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows who hid each event, when and why', async () => {
    const { row } = await render();

    expect(row('Jazz night').textContent).toContain('Hidden by Shadi');
    expect(row('Jazz night').textContent).toContain('Mon, 16 Nov 2026, 21:00');
    expect(row('Jazz night').textContent).toContain('Duplicate listing');
    expect(row('e2').textContent).toContain('Hidden by a deleted admin');
    expect(row('Jazz night').textContent).toContain('Duplicate listing');
    expect(row('Jazz night').querySelector('a')).toBeNull();
  });

  it('unhides an event and loads the list again', async () => {
    const { fixture, el, http, unhide } = await render();
    unhide('Jazz night').click();

    const req = http.expectOne('/api/admin/events/e1/hide');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    http.expectOne(LIST).flush([HIDDEN[1]]);
    await fixture.whenStable();

    expect(el.textContent).not.toContain('Duplicate listing');
  });

  it('shows a failed unhide on its row', async () => {
    const { fixture, http, row, unhide } = await render();
    unhide('e2').click();
    http.expectOne('/api/admin/events/e2/hide').error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(row('e2').querySelector('[role="alert"]')?.textContent).toContain(
      "We couldn't unhide this event.",
    );
  });

  it('says when nothing is hidden', async () => {
    const { el } = await render([]);
    expect(el.textContent).toContain('No hidden events.');
  });

  it('shows a retryable error when the list fails to load', async () => {
    const { el } = await render('error');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      "We couldn't load the hidden events.",
    );
  });
});
