import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { OrganizerDetail } from '../../core/models';
import { OrganizerPage } from './organizer-page';

const detail: OrganizerDetail = {
  organizer: {
    id: 'o1',
    name: 'Bimhuis',
    description: 'Jazz venue on the IJ.',
    site: 'www.bimhuis.nl',
  },
  events: {
    page: 1,
    size: 20,
    totalElements: 21,
    totalPages: 2,
    items: [
      {
        id: 'e1',
        title: 'Late-night jazz session',
        startAt: '2026-10-10T18:30:00Z',
        endAt: '2026-10-10T21:00:00Z',
        place: 'PHYSICAL',
        citySlug: 'amsterdam',
        cityName: 'Amsterdam',
        venueName: 'Bimhuis',
        imageUrl: null,
      },
    ],
  },
};

async function open(url: string) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter(
        [{ path: 'organizers/:id', component: OrganizerPage }],
        withComponentInputBinding(),
      ),
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url);
  return { harness, http: TestBed.inject(HttpTestingController) };
}

describe('OrganizerPage', () => {
  it('requests the page from the URL and shows profile, safe website link, events and pagination', async () => {
    const { harness, http } = await open('/organizers/o1?page=1');
    http.expectOne('/api/organizers/o1?page=1').flush(detail);
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement!;

    expect(el.querySelector('h1')?.textContent).toContain('Bimhuis');
    expect(el.textContent).toContain('Jazz venue on the IJ.');
    const site = el.querySelector('a[target="_blank"]');
    expect(site?.getAttribute('href')).toBe('https://www.bimhuis.nl');
    expect(site?.getAttribute('rel')).toBe('noopener noreferrer');
    expect(el.querySelector('a[href="/events/e1"]')).toBeTruthy();
    expect(el.querySelector('[aria-current="page"]')?.textContent).toContain('2');
    expect(TestBed.inject(Title).getTitle()).toBe('Bimhuis – Loc');
  });

  it('treats a garbage page number as the first page', async () => {
    const { http } = await open('/organizers/o1?page=abc');
    http.expectOne('/api/organizers/o1?page=0');
  });

  it('shows an empty state when the page has no events', async () => {
    const { harness, http } = await open('/organizers/o1?page=5');
    http
      .expectOne('/api/organizers/o1?page=5')
      .flush({ ...detail, events: { ...detail.events, page: 5, items: [] } });
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement!.textContent).toContain('Back to the first page');
  });

  it('shows a not-found state on 404', async () => {
    const { harness, http } = await open('/organizers/o1');
    http
      .expectOne('/api/organizers/o1?page=0')
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    await harness.fixture.whenStable();
    expect(harness.routeNativeElement!.textContent).toContain('Organizer not found');
  });
});
