import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthSession } from '../../core/auth-session';
import { OrganizerInfo } from '../../core/models';
import { MePage } from './me-page';

const organizers: OrganizerInfo[] = [
  { id: 'o1', name: 'Bimhuis', description: 'Jazz venue on the IJ.', site: null },
  { id: 'o2', name: 'Paradiso', description: null, site: null },
];

const emptySaved = { page: 0, size: 20, totalElements: 0, totalPages: 0, items: [] };

async function open(url: string) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter(
        [
          { path: 'me', component: MePage },
          { path: 'saved', redirectTo: 'me' },
        ],
        withComponentInputBinding(),
      ),
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url);
  const el = () => harness.routeNativeElement!;
  const tab = (label: string) =>
    [...el().querySelectorAll('nav a')].find((a) => a.textContent?.trim() === label);
  return { harness, el, tab, http: TestBed.inject(HttpTestingController) };
}

describe('MePage', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('opens on the Saved tab, with both tabs as links', async () => {
    const { harness, http, el, tab } = await open('/me');
    http.expectOne('/api/me/saved-events?page=0').flush(emptySaved);
    await harness.fixture.whenStable();

    expect(el().querySelector('h1')?.textContent).toContain('My Loc');
    expect(tab('Saved')?.getAttribute('aria-current')).toBe('page');
    expect(tab('Following')?.getAttribute('aria-current')).toBeNull();
    expect(tab('Following')?.getAttribute('href')).toBe('/me?tab=following');
    expect(el().textContent).toContain('No saved events yet');
  });

  it('switches to the Following tab and drops the Saved page number', async () => {
    const { harness, http, tab } = await open('/me?page=2');
    http.expectOne('/api/me/saved-events?page=2').flush({ ...emptySaved, page: 2 });
    await harness.fixture.whenStable();

    (tab('Following') as HTMLAnchorElement).click();
    // Not whenStable(): it would wait for the organizers request this test answers next.
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/me?tab=following'));
    http.expectOne('/api/me/followed-organizers').flush(organizers);
    await harness.fixture.whenStable();
    expect(tab('Following')?.getAttribute('aria-current')).toBe('page');
  });

  it('lists followed organizers as cards that open the organizer, each with a Follow toggle', async () => {
    const { harness, http, el } = await open('/me?tab=following');
    http.expectOne('/api/me/followed-organizers').flush(organizers);
    await harness.fixture.whenStable();

    expect(el().textContent).toContain('Following 2 organizers.');
    const cards = el().querySelectorAll('ul[role="list"] > li');
    expect(cards.length).toBe(2);
    expect(cards[0].querySelector('a')?.getAttribute('href')).toBe('/organizers/o1');
    expect(cards[0].textContent).toContain('Jazz venue on the IJ.');
    const button = cards[0].querySelector('button');
    expect(button?.getAttribute('aria-pressed')).toBe('true');
    expect(button?.closest('a')).toBeNull();
  });

  it('unfollows from the list but keeps the card until the next load', async () => {
    const { harness, http, el } = await open('/me?tab=following');
    http.expectOne('/api/me/followed-organizers').flush(organizers);
    await harness.fixture.whenStable();

    // The button needs a signed-in session; without one it would go to login instead.
    TestBed.inject(AuthSession).start('token');
    const button = el().querySelector<HTMLButtonElement>('ul[role="list"] > li button')!;
    button.click();
    await harness.fixture.whenStable();

    expect(http.expectOne('/api/me/followed-organizers/o1').request.method).toBe('DELETE');
    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(el().querySelectorAll('ul[role="list"] > li').length).toBe(2);
  });

  it('shows an empty state when the member follows nobody', async () => {
    const { harness, http, el } = await open('/me?tab=following');
    http.expectOne('/api/me/followed-organizers').flush([]);
    await harness.fixture.whenStable();

    expect(el().textContent).toContain("You don't follow any organizers yet");
    expect(el().querySelector('a[href="/"]')).not.toBeNull();
  });

  it('shows the retryable error state when the organizers cannot load', async () => {
    const { harness, http, el } = await open('/me?tab=following');
    http
      .expectOne('/api/me/followed-organizers')
      .flush({ status: 500 }, { status: 500, statusText: 'Server Error' });
    await harness.fixture.whenStable();
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Try again');
  });

  it('sends the old /saved link to My Loc', async () => {
    const { http } = await open('/saved');
    expect(TestBed.inject(Router).url).toBe('/me');
    http.expectOne('/api/me/saved-events?page=0').flush(emptySaved);
  });
});
