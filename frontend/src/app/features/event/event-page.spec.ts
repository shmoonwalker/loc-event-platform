import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
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
    { startAt: '2026-10-10T18:30:00Z', endAt: '2026-10-10T21:00:00Z' },
    { startAt: '2026-10-17T18:30:00Z', endAt: '2026-10-17T18:30:00Z' },
  ],
  weather: {
    forecastFetchedAt: '2026-10-06T06:00:00Z',
    forecastHour: '2026-10-10T18:00:00Z',
    temperatureCelsius: 14.6,
    precipitationProbabilityPercent: 40,
    windSpeedKmh: 18,
    weatherCode: 63,
  },
};

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
  it('shows the event in Amsterdam time, with location, weather, organizers and more dates', async () => {
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

  it('links categories and tags to the filtered home list', async () => {
    const el = await open((req) => req.flush(event));
    expect(el.querySelector('a[href="/?category=music-nightlife#all"]')).toBeTruthy();
    expect(el.querySelector('a[href="/?tag=live-music#all"]')).toBeTruthy();
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
    expect(el.querySelector('#where-title')).toBeNull();
    expect(el.querySelector('#weather-title')).toBeNull();
  });

  it('shows a not-found state on 404, not the generic error', async () => {
    const el = await open((req) =>
      req.flush({ status: 404 }, { status: 404, statusText: 'Not Found' }),
    );
    expect(el.textContent).toContain('Event not found');
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows the retryable error state on other failures', async () => {
    const el = await open((req) =>
      req.flush({ status: 500 }, { status: 500, statusText: 'Server Error' }),
    );
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Try again');
  });
});
