import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
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
});
