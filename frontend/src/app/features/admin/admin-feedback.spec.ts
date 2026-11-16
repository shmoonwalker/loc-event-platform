import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Feedback, FeedbackPage } from '../../core/admin-api';
import { AdminFeedback } from './admin-feedback';

const OPEN: Feedback = {
  id: 'f1',
  userId: 'u1',
  message: 'The map is hard to use on a phone.',
  createdAt: '2026-11-16T20:00:00Z',
};
const VISITOR: Feedback = {
  id: 'f2',
  message: 'Please add Haarlem.',
  contactEmail: 'anouk@example.com',
  createdAt: '2026-11-15T09:30:00Z',
};
const HANDLED: Feedback = {
  id: 'f3',
  message: 'Typo on the home page.',
  createdAt: '2026-11-01T10:00:00Z',
  handledAt: '2026-11-02T10:00:00Z',
  handledBy: 'a1',
};

const pageOf = (items: Feedback[], over: Partial<FeedbackPage> = {}): FeedbackPage => ({
  page: 0,
  size: 20,
  totalElements: items.length,
  totalPages: items.length ? 1 : 0,
  items,
  ...over,
});

async function render(first: FeedbackPage | 'error' = pageOf([OPEN, VISITOR, HANDLED])) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AdminFeedback);
  fixture.detectChanges();
  if (first === 'error')
    http.expectOne('/api/admin/feedback?page=0').error(new ProgressEvent('error'));
  else http.expectOne('/api/admin/feedback?page=0').flush(first);
  await fixture.whenStable();

  const el = fixture.nativeElement as HTMLElement;
  const tick = () => fixture.detectChanges();
  const row = (text: string) =>
    [...el.querySelectorAll('li')].find((li) => li.textContent?.includes(text))!;
  const button = (label: string, within: ParentNode = el) =>
    [...within.querySelectorAll('button')].find((b) => b.textContent?.trim() === label);
  return { fixture, el, http, tick, row, button };
}

describe('AdminFeedback', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows each message with its state, date, sender and contact email', async () => {
    const { row, button } = await render();

    expect(row('hard to use').textContent).toContain('Open');
    expect(row('hard to use').textContent).toContain('Mon, 16 Nov 2026, 21:00');
    expect(row('hard to use').textContent).toContain('From a member');
    expect(row('Haarlem').textContent).toContain('From a visitor');
    expect(row('Haarlem').querySelector('a')?.getAttribute('href')).toBe(
      'mailto:anouk@example.com',
    );
    expect(row('Typo').textContent).toContain('Handled');
    expect(button('Mark handled', row('Typo'))).toBeUndefined();
    expect(button('Mark handled', row('hard to use'))).toBeDefined();
  });

  it('marks feedback handled and loads the page again', async () => {
    const { fixture, http, tick, row, button } = await render();
    button('Mark handled', row('hard to use'))!.click();

    const req = http.expectOne('/api/admin/feedback/f1/handled');
    expect(req.request.method).toBe('POST');
    req.flush({ ...OPEN, handledAt: '2026-11-16T21:00:00Z' });
    tick();
    http
      .expectOne('/api/admin/feedback?page=0')
      .flush(pageOf([VISITOR, { ...OPEN, handledAt: '2026-11-16T21:00:00Z' }, HANDLED]));
    await fixture.whenStable();

    expect(button('Mark handled', row('hard to use'))).toBeUndefined();
  });

  it('shows a failed update on its row', async () => {
    const { http, tick, row, button } = await render();
    button('Mark handled', row('Haarlem'))!.click();
    http.expectOne('/api/admin/feedback/f2/handled').error(new ProgressEvent('error'));
    tick();

    expect(row('Haarlem').querySelector('[role="alert"]')?.textContent).toContain(
      "We couldn't mark this feedback handled.",
    );
  });

  it('pages through the feedback', async () => {
    const { el, fixture, http, tick } = await render(
      pageOf([OPEN], { totalElements: 21, totalPages: 2 }),
    );
    el.querySelector<HTMLButtonElement>('[aria-label="Page 2"]')!.click();
    tick();

    http
      .expectOne('/api/admin/feedback?page=1')
      .flush(pageOf([HANDLED], { page: 1, totalElements: 21, totalPages: 2 }));
    await fixture.whenStable();
    expect(el.textContent).toContain('Typo on the home page.');
  });

  it('says when there is no feedback', async () => {
    const { el } = await render(pageOf([]));
    expect(el.textContent).toContain('No feedback yet.');
  });

  it('shows a retryable error when the feedback fails to load', async () => {
    const { el } = await render('error');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      "We couldn't load the feedback.",
    );
  });
});
