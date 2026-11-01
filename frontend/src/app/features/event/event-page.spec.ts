import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthSession } from '../../core/auth-session';
import { EventDetail } from '../../core/models';
import { EventPage } from './event-page';

const event: EventDetail = {
  id: 'e1',
  title: 'Late-night jazz session',
  description: 'Bring friends.\nDoors at 20:00.',
  place: 'PHYSICAL',
  sourceUrl: 'https://example.nl/jazz',
  venueName: 'Bimhuis',
  address: 'Piet Heinkade 3',
  postalCode: '1019 BR',
  citySlug: 'amsterdam',
  cityName: 'Amsterdam',
  latitude: 52.3765,
  longitude: 4.9126,
  categories: [{ value: 'music-nightlife', label: 'Music & nightlife' }],
  tags: [{ value: 'live-music', label: 'Live music' }],
  imageUrls: [],
  organizers: [{ id: 'o1', name: 'Bimhuis', description: null, site: null }],
  occurrences: [
    {
      startAt: '2026-10-10T18:30:00Z',
      endAt: '2026-10-10T21:00:00Z',
      sourceUrl: 'https://example.nl/jazz',
    },
    {
      startAt: '2026-10-17T18:30:00Z',
      endAt: '2026-10-17T18:30:00Z',
      sourceUrl: 'https://example.nl/jazz-17',
      weather: {
        forecastFetchedAt: '2026-10-06T06:00:00Z',
        forecastHour: '2026-10-17T18:00:00Z',
        temperatureCelsius: 11,
        precipitationProbabilityPercent: null,
        windSpeedKmh: null,
        weatherCode: 0,
      },
    },
  ],
  weather: {
    forecastFetchedAt: '2026-10-06T06:00:00Z',
    forecastHour: '2026-10-10T18:00:00Z',
    temperatureCelsius: 14.6,
    precipitationProbabilityPercent: 40,
    windSpeedKmh: 18,
    weatherCode: 63,
  },
  saved: false,
  saveCount: 0,
  available: true,
};

const oneDate = { ...event, occurrences: [event.occurrences[0]] };

/** Re-render after a change made outside the template (an event, a sign-in). */
const settle = () => TestBed.inject(ApplicationRef).whenStable();

/** True when a comes before b in the page. */
const before = (a: Element | null, b: Element | null) =>
  !!a && !!b && !!(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);

async function open(
  respond: (req: ReturnType<HttpTestingController['expectOne']>) => void,
): Promise<HTMLElement> {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([{ path: 'events/:id', component: EventPage }], withComponentInputBinding()),
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl('/events/e1');
  respond(TestBed.inject(HttpTestingController).expectOne('/api/events/e1'));
  await harness.fixture.whenStable();
  return harness.routeNativeElement!;
}

describe('EventPage', () => {
  it('shows the event in Amsterdam time, with location, weather, organizers and all dates', async () => {
    const el = await open((req) => req.flush(event));
    expect(el.querySelector('h1')?.textContent).toContain('Late-night jazz session');
    expect(el.textContent).toContain('Sat, 10 Oct 2026, 20:30 – 23:00');
    expect(el.textContent).toContain('Sat, 17 Oct 2026, 20:30');
    expect(el.textContent).toContain('1019 BR Amsterdam');
    expect(el.textContent).toContain('15°C, Rain');
    expect(el.querySelector('a[href="/organizers/o1"]')?.textContent).toContain('Bimhuis');
    expect(TestBed.inject(Title).getTitle()).toBe('Late-night jazz session – Loc');
  });

  it('uses the first image, else the category photo the card falls back to', async () => {
    const el = await open((req) =>
      req.flush({ ...event, imageUrls: ['https://images.example/jazz.jpg'] }),
    );
    expect(el.querySelector('img')?.getAttribute('src')).toBe('https://images.example/jazz.jpg');
    expect(el.querySelector('img')?.getAttribute('alt')).toBe('Late-night jazz session');
    TestBed.resetTestingModule();
    const fallback = await open((req) => req.flush(event));
    expect(fallback.querySelector('img')?.getAttribute('src')).toBe(
      '/images/categories/music-nightlife.jpg',
    );
    expect(fallback.querySelector('img')?.getAttribute('alt')).toBe('');
  });

  it('swaps a listing image that fails to load for the category photo', async () => {
    const el = await open((req) =>
      req.flush({ ...event, imageUrls: ['https://images.example/dead.jpg'] }),
    );
    el.querySelector('img')!.dispatchEvent(new Event('error'));
    await settle();
    expect(el.querySelector('img')?.getAttribute('src')).toBe(
      '/images/categories/music-nightlife.jpg',
    );
    expect(el.querySelector('img')?.getAttribute('alt')).toBe('');
  });

  it('links categories above the title and tags under the description', async () => {
    const el = await open((req) => req.flush(event));
    const category = el.querySelector('a[href="/?category=music-nightlife#all"]');
    const tag = el.querySelector('a[href="/?tag=live-music#all"]');
    expect(before(category, el.querySelector('h1'))).toBe(true);
    expect(before(el.querySelector('.whitespace-pre-line'), tag)).toBe(true);
  });

  it('shows the full address under the date, with no separate "Where" section', async () => {
    const el = await open((req) => req.flush(event));
    expect(el.textContent).toContain('Bimhuis, Piet Heinkade 3, 1019 BR Amsterdam');
    expect(el.textContent).not.toContain('Where');
  });

  it('shows a map at the coordinates after the weather card, with a maps link under it', async () => {
    const el = await open((req) => req.flush(event));
    const map = el.querySelector('iframe');
    expect(map?.getAttribute('src')).toContain('openstreetmap.org/export/embed.html');
    expect(map?.getAttribute('src')).toContain('marker=52.3765,4.9126');
    expect(before(el.querySelector('#weather-title'), map)).toBe(true);
    expect(before(map, el.querySelector('a[href*="google.com/maps"]'))).toBe(true);
  });

  it('shows no map when the event has no coordinates', async () => {
    const el = await open((req) => req.flush({ ...event, latitude: null, longitude: null }));
    expect(el.querySelector('iframe')).toBeNull();
    expect(el.querySelector('#map-title')).toBeNull();
  });

  it('says why there is no forecast: too early, or not available', async () => {
    const far = { startAt: '2099-01-01T18:00:00Z', endAt: '2099-01-01T18:00:00Z', sourceUrl: null };
    const el = await open((req) => req.flush({ ...oneDate, occurrences: [far], weather: null }));
    expect(el.textContent).toContain("It's too early for a forecast.");
    TestBed.resetTestingModule();
    const near = await open((req) => req.flush({ ...oneDate, weather: null }));
    expect(near.textContent).toContain("Weather isn't available right now.");
  });

  it('shows one date as a plain line, without the date picker', async () => {
    const el = await open((req) => req.flush(oneDate));
    expect(el.textContent).toContain('Sat, 10 Oct 2026, 20:30 – 23:00');
    expect(el.querySelector('select')).toBeNull();
    expect(el.textContent).not.toContain('dates');
  });

  it('lets you pick a date, which updates the ticket link and the weather', async () => {
    const el = await open((req) => req.flush(event));
    expect(el.textContent).toContain('2 dates');
    expect(el.querySelector('label')?.textContent).toContain('Choose a date');
    expect(el.textContent).toContain('15°C, Rain'); // the first date: the top-level forecast

    const select = el.querySelector('select')!;
    select.value = '1';
    select.dispatchEvent(new Event('change'));
    await settle();

    expect(el.querySelector('a[href="https://example.nl/jazz-17"]')?.textContent).toContain(
      'Tickets & more info',
    );
    expect(el.querySelector('a[href="https://example.nl/jazz"]')).toBeNull();
    expect(el.textContent).toContain('11°C, Clear sky');
  });

  it('shows "More like this" to everyone and "More for you" only when logged in', async () => {
    const el = await open((req) => req.flush(event));
    expect(el.querySelector('#similar-title')?.textContent).toContain('More like this');
    expect(el.querySelector('#for-you-title')).toBeNull();

    TestBed.inject(AuthSession).start('token');
    await settle();
    expect(el.querySelector('#for-you-title')?.textContent).toContain('More for you');
    expect(el.textContent).toContain('Coming soon');
  });

  it('ends the description with one "Tickets & more info" link to the listing', async () => {
    const el = await open((req) => req.flush(event));
    const links = [...el.querySelectorAll('a[href="https://example.nl/jazz"]')];
    expect(links.length).toBe(1);
    expect(links[0].textContent).toContain('Tickets & more info');
    expect(el.textContent).not.toContain('View the original listing');
  });

  it('shows the fallback line when there is no description', async () => {
    const el = await open((req) => req.flush({ ...event, description: null }));
    expect(el.textContent).toContain('For more info, visit the event listing');
    expect(el.querySelector('a[href="https://example.nl/jazz"]')).toBeTruthy();
    expect(el.textContent).not.toContain('Tickets & more info');
  });

  it('leaves out both links when the event has no usable source link', async () => {
    const unsafe = 'javascript:alert(1)';
    const occurrences = event.occurrences.map((o) => ({ ...o, sourceUrl: unsafe }));
    const el = await open((req) => req.flush({ ...event, sourceUrl: unsafe, occurrences }));
    expect(el.textContent).not.toContain('Tickets & more info');
    TestBed.resetTestingModule();
    const none = event.occurrences.map((o) => ({ ...o, sourceUrl: null }));
    const bare = await open((req) =>
      req.flush({ ...event, description: null, sourceUrl: null, occurrences: none }),
    );
    expect(bare.textContent).not.toContain('For more info');
  });

  it('shows "Online" and no location or weather block for online events', async () => {
    const online = {
      ...event,
      place: 'ONLINE' as const,
      venueName: null,
      address: null,
      postalCode: null,
      cityName: null,
      latitude: null,
      longitude: null,
      weather: null,
    };
    const el = await open((req) => req.flush(online));
    expect(el.textContent).toContain('Online');
    expect(el.querySelector('#weather-title')).toBeNull();
    expect(el.querySelector('iframe')).toBeNull();
  });

  it('shows a not-found state on 404, not the generic error', async () => {
    const el = await open((req) =>
      req.flush({ status: 404 }, { status: 404, statusText: 'Not Found' }),
    );
    expect(el.textContent).toContain('Event not found');
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows the save button with the saved state from the API', async () => {
    const el = await open((req) => req.flush({ ...event, saved: true }));
    expect(el.querySelector('button[aria-label="Save event"]')?.getAttribute('aria-pressed')).toBe(
      'true',
    );
    expect(el.textContent).not.toContain('no longer available');
  });

  it('opens a saved event that is no longer public through the saved events API', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'events/:id', component: EventPage }], withComponentInputBinding()),
      ],
    });
    TestBed.inject(AuthSession).start('token');
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/events/e1');
    http
      .expectOne('/api/events/e1')
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/me/saved-events/e1').flush({ ...event, saved: true, available: false });
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement!;
    expect(el.querySelector('h1')?.textContent).toContain('Late-night jazz session');
    expect(el.textContent).toContain('This event is no longer available');
  });

  it('shows the retryable error state on other failures', async () => {
    const el = await open((req) =>
      req.flush({ status: 500 }, { status: 500, statusText: 'Server Error' }),
    );
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Try again');
  });
});
