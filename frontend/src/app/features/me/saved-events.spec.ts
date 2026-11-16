import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthSession } from '../../core/auth-session';
import { EventCard, SavedEventPage } from '../../core/models';
import { MePage } from './me-page';

const card = (id: string, title: string): EventCard => ({
  id,
  title,
  startAt: '2026-10-10T18:30:00Z',
  endAt: '2026-10-10T21:00:00Z',
  place: 'PHYSICAL',
  citySlug: 'amsterdam',
  cityName: 'Amsterdam',
  venueName: 'Bimhuis',
  imageUrl: null,
  saved: true,
});

const twoPages: SavedEventPage = {
  page: 1,
  size: 20,
  totalElements: 22,
  totalPages: 2,
  items: [
    {
      event: card('e1', 'Late-night jazz session'),
      savedAt: '2026-10-01T10:00:00Z',
      available: true,
    },
    { event: card('e2', 'Old market'), savedAt: '2026-09-01T10:00:00Z', available: false },
  ],
};

async function open(url: string) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([{ path: 'me', component: MePage }], withComponentInputBinding()),
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url);
  return { harness, http: TestBed.inject(HttpTestingController) };
}

describe('SavedEvents (the Saved tab of My Loc)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('requests the page from the URL and lists saved events, marking unavailable ones', async () => {
    const { harness, http } = await open('/me?page=1');
    http.expectOne('/api/me/saved-events?page=1').flush(twoPages);
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement!;

    expect(el.textContent).toContain('22 saved events.');
    const items = el.querySelectorAll('ul[role="list"] > li');
    expect(items.length).toBe(2);
    expect(items[0].textContent).not.toContain('No longer available');
    expect(items[1].textContent).toContain('No longer available');
    expect(items[1].querySelector('a')?.getAttribute('href')).toBe('/events/e2');
    expect(el.querySelector('app-pagination')).not.toBeNull();
  });

  it('shows an empty state with a way to find events', async () => {
    const { harness, http } = await open('/me');
    http
      .expectOne('/api/me/saved-events?page=0')
      .flush({ page: 0, size: 20, totalElements: 0, totalPages: 0, items: [] });
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement!;

    expect(el.textContent).toContain('No saved events yet');
    expect(el.querySelector('a[href="/"]')).not.toBeNull();
  });

  it('shows the retryable error state when the list cannot load', async () => {
    const { harness, http } = await open('/me');
    http
      .expectOne('/api/me/saved-events?page=0')
      .flush({ status: 500 }, { status: 500, statusText: 'Server Error' });
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')?.textContent).toContain(
      'Try again',
    );
  });

  describe('unsaving', () => {
    /** Signed in, so the heart calls the API instead of opening the login page. */
    async function openSaved(url: string, page: SavedEventPage) {
      const opened = await open(url);
      TestBed.inject(AuthSession).start('token');
      opened.http.expectOne(`/api/me/saved-events?page=${page.page}`).flush(page);
      await opened.harness.fixture.whenStable();
      const el = opened.harness.routeNativeElement!;
      const cards = () => el.querySelectorAll('ul[role="list"] > li');
      const heart = (i: number) => cards()[i].querySelector<HTMLButtonElement>('button')!;
      const status = () => el.querySelector('p[aria-live="polite"]')!;
      return { ...opened, el, cards, heart, status };
    }

    it('removes the card at once, says Removed and counts one less', async () => {
      const { harness, http, cards, heart, status } = await openSaved('/me?page=1', twoPages);
      heart(0).click();
      const req = http.expectOne('/api/me/saved-events/e1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
      await harness.fixture.whenStable();

      expect(cards().length).toBe(1);
      expect(cards()[0].textContent).toContain('Old market');
      expect(status().textContent).toContain('Removed.');
      expect(status().textContent).toContain('21 saved events.');
      expect(document.activeElement).toBe(status());
    });

    it('removes an event that is no longer available the same way', async () => {
      const { harness, http, cards, heart, status } = await openSaved('/me?page=1', twoPages);
      heart(1).click();
      http
        .expectOne('/api/me/saved-events/e2')
        .flush(null, { status: 204, statusText: 'No Content' });
      await harness.fixture.whenStable();

      expect(cards().length).toBe(1);
      expect(cards()[0].textContent).toContain('Late-night jazz session');
      expect(status().textContent).toContain('Removed.');
    });

    it('keeps the card when the server refuses', async () => {
      const { harness, http, cards, heart, status } = await openSaved('/me?page=1', twoPages);
      heart(0).click();
      http
        .expectOne('/api/me/saved-events/e1')
        .flush(null, { status: 500, statusText: 'Server Error' });
      await harness.fixture.whenStable();

      expect(cards().length).toBe(2);
      expect(heart(0).getAttribute('aria-pressed')).toBe('true');
      expect(status().textContent).not.toContain('Removed.');
    });

    it('shows the empty state after the last saved event is removed', async () => {
      const one: SavedEventPage = {
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        items: [twoPages.items[0]],
      };
      const { harness, http, el, heart, status } = await openSaved('/me', one);
      heart(0).click();
      http
        .expectOne('/api/me/saved-events/e1')
        .flush(null, { status: 204, statusText: 'No Content' });
      await harness.fixture.whenStable();

      expect(el.textContent).toContain('No saved events yet');
      expect(status().textContent).toContain('Removed.');
      expect(status().textContent).not.toContain('saved event');
    });

    it('opens the previous page when the last page becomes empty', async () => {
      const lastPage: SavedEventPage = {
        page: 1,
        size: 20,
        totalElements: 21,
        totalPages: 2,
        items: [twoPages.items[0]],
      };
      const { harness, http, heart } = await openSaved('/me?page=1', lastPage);
      heart(0).click();
      http
        .expectOne('/api/me/saved-events/e1')
        .flush(null, { status: 204, statusText: 'No Content' });

      await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/me'));
      http
        .expectOne('/api/me/saved-events?page=0')
        .flush({ ...lastPage, page: 0, totalElements: 20, totalPages: 1 });
      await harness.fixture.whenStable();
    });

    it('reloads a page that becomes empty while later pages remain', async () => {
      const middle: SavedEventPage = {
        page: 0,
        size: 1,
        totalElements: 3,
        totalPages: 3,
        items: [twoPages.items[0]],
      };
      const { harness, http, cards, heart } = await openSaved('/me', middle);
      heart(0).click();
      http
        .expectOne('/api/me/saved-events/e1')
        .flush(null, { status: 204, statusText: 'No Content' });

      await vi.waitFor(() =>
        http.expectOne('/api/me/saved-events?page=0').flush({
          ...middle,
          totalElements: 2,
          totalPages: 2,
          items: [twoPages.items[1]],
        }),
      );
      await harness.fixture.whenStable();
      expect(cards()[0].textContent).toContain('Old market');
    });

    it('stops saying Removed after moving to another page', async () => {
      const { harness, http, heart, status } = await openSaved('/me?page=1', twoPages);
      heart(0).click();
      http
        .expectOne('/api/me/saved-events/e1')
        .flush(null, { status: 204, statusText: 'No Content' });
      await harness.fixture.whenStable();
      expect(status().textContent).toContain('Removed.');

      await harness.navigateByUrl('/me?page=0');
      http.expectOne('/api/me/saved-events?page=0').flush({ ...twoPages, page: 0 });
      await harness.fixture.whenStable();
      expect(status().textContent).not.toContain('Removed.');
    });
  });
});
