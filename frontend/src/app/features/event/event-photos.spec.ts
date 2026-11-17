import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthSession } from '../../core/auth-session';
import { EventPhoto } from '../../core/photos-api';
import { EventPhotos } from './event-photos';

const photo = (over: Partial<EventPhoto> = {}): EventPhoto => ({
  id: 'p1',
  eventId: 'e1',
  status: 'APPROVED',
  cardUrl: 'https://photos.example/p1/card.jpg',
  fullUrl: 'https://photos.example/p1/full.jpg',
  createdAt: '2026-11-16T21:00:00Z',
  uploader: { displayName: 'Anouk' },
  ...over,
});

const newer = photo({
  id: 'p2',
  cardUrl: 'https://photos.example/p2/card.jpg',
  fullUrl: 'https://photos.example/p2/full.jpg',
  createdAt: '2026-11-16T22:00:00Z',
  uploader: { displayName: 'Bo' },
});
const older = photo();
const mineApproved = photo({
  id: 'p5',
  cardUrl: 'https://photos.example/mine/card.jpg',
  fullUrl: 'https://photos.example/mine/full.jpg',
  uploader: { displayName: 'Shadi' },
});
const waiting = photo({
  id: 'p3',
  status: 'PENDING',
  cardUrl: '/api/events/e1/photos/p3/file?variant=card',
  fullUrl: '/api/events/e1/photos/p3/file?variant=full',
  uploader: { displayName: 'Shadi' },
});
const rejected = photo({
  id: 'p4',
  status: 'REJECTED',
  cardUrl: '/api/events/e1/photos/p4/file?variant=card',
  fullUrl: '/api/events/e1/photos/p4/file?variant=full',
  uploader: { displayName: 'Shadi' },
});

const PUBLIC = '/api/events/e1/photos';
const MINE = '/api/me/events/e1/photos';

async function render(signedIn: boolean) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });
  if (signedIn) TestBed.inject(AuthSession).start('token');
  const fixture = TestBed.createComponent(EventPhotos);
  fixture.componentRef.setInput('eventId', 'e1');
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();

  const el = fixture.nativeElement as HTMLElement;
  const flush = async (pub: EventPhoto[], mine: EventPhoto[] = []) => {
    http.expectOne({ method: 'GET', url: PUBLIC }).flush(pub);
    if (signedIn) http.expectOne({ method: 'GET', url: MINE }).flush(mine);
    await fixture.whenStable();
  };
  const tick = () => fixture.detectChanges();
  const settle = () => fixture.whenStable();
  const button = (label: string, within: ParentNode = el) =>
    [...within.querySelectorAll('button')].find((b) => b.textContent?.trim() === label);
  const gridItem = (name: string) =>
    [...el.querySelectorAll('#photo-grid li')].find((item) => item.textContent?.includes(name));
  const ownItem = (text: string) =>
    [...el.querySelectorAll('#your-photos li')].find((item) => item.textContent?.includes(text))!;
  const pick = (file: File) => {
    const input = el.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    input.dispatchEvent(new Event('change'));
    tick();
  };
  return { el, http, flush, tick, settle, button, gridItem, ownItem, pick };
}

describe('EventPhotos', () => {
  // jsdom has no modal dialogs; the profile specs stub the same two methods.
  beforeEach(() => {
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
    };
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows approved photos newest first, and opens the URL the API gave', async () => {
    const { el, flush, tick } = await render(false);
    await flush([newer, older]);

    const sources = [...el.querySelectorAll('#photo-grid img')].map((img) =>
      img.getAttribute('src'),
    );
    expect(sources).toEqual([newer.cardUrl, older.cardUrl]);
    expect(el.textContent).toContain('Bo');
    expect(el.textContent).toContain('Anouk');
    expect(el.querySelector('input[type="file"]')).toBeNull();

    el.querySelector<HTMLButtonElement>('#photo-grid button')!.click();
    tick();
    const dialog = el.querySelector<HTMLDialogElement>('#photo-viewer')!;
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector('img')?.getAttribute('src')).toBe(newer.fullUrl);
    expect(dialog.querySelector('img')?.getAttribute('alt')).toBe('Photo by Bo');
  });

  it('says there are no photos yet, and offers no upload or report when signed out', async () => {
    const { el, http, flush, button } = await render(false);
    await flush([]);

    expect(el.querySelector('#photos-title')?.textContent).toContain('Photos');
    expect(el.textContent).toContain('There are no photos yet.');
    expect(el.querySelector('input[type="file"]')).toBeNull();
    expect(el.textContent).not.toContain('You own this photo');
    expect(button('Report')).toBeUndefined();
    http.expectNone(MINE);
  });

  it('keeps your own photos out of the public grid and lets you open them', async () => {
    const { el, flush, tick, button, gridItem, ownItem } = await render(true);
    await flush([newer, mineApproved], [mineApproved, waiting, rejected]);

    expect(gridItem('Bo')).toBeTruthy();
    expect(gridItem('Shadi')).toBeUndefined();
    expect(button('Report', gridItem('Bo'))).toBeTruthy();
    expect(ownItem('Waiting for approval').querySelector('img')?.getAttribute('src')).toBe(
      waiting.cardUrl,
    );
    expect(ownItem('Not approved')).toBeTruthy();
    expect(button('Report', ownItem('Waiting for approval'))).toBeUndefined();
    expect(button('Delete', ownItem('Not approved'))).toBeTruthy();
    expect(el.textContent).toContain(
      'You own this photo and agree it can be shown. Someone in the photo can ask for it to be removed.',
    );

    ownItem('Waiting for approval').querySelector('button')!.click();
    tick();
    expect(el.querySelector('#photo-viewer img')?.getAttribute('src')).toBe(waiting.fullUrl);
  });

  it('uploads a picture into your own list, not the public grid', async () => {
    const { el, http, flush, pick, tick } = await render(true);
    await flush([older], []);
    const file = new File([new Uint8Array([1, 2, 3])], 'night.jpg', { type: 'image/jpeg' });
    pick(file);

    const req = http.expectOne({ method: 'POST', url: PUBLIC });
    expect(req.request.body).toBeInstanceOf(FormData);
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(waiting, { status: 201, statusText: 'Created' });
    tick();

    expect(el.querySelector('#your-photos')?.textContent).toContain('Waiting for approval');
    expect(el.querySelector('#your-photos img')?.getAttribute('src')).toBe(waiting.cardUrl);
    const gridSources = [...el.querySelectorAll('#photo-grid img')].map((img) =>
      img.getAttribute('src'),
    );
    expect(gridSources).toEqual([older.cardUrl]);
  });

  it('shows wrong type, size, the 10 photo limit and the rate limit on the form', async () => {
    const { el, http, flush, pick, tick } = await render(true);
    await flush([], []);
    const file = new File([new Uint8Array([1])], 'notes.txt', { type: 'text/plain' });
    const send = (
      status: number,
      statusText: string,
      body: object | null = null,
      retryAfter?: string,
    ) => {
      pick(file);
      http.expectOne({ method: 'POST', url: PUBLIC }).flush(body, {
        status,
        statusText,
        headers: retryAfter ? { 'Retry-After': retryAfter } : undefined,
      });
      tick();
    };

    send(400, 'Bad Request', { title: 'Bad Request' });
    expect(el.querySelector('#photo-upload-error')?.textContent).toContain(
      'Choose a JPEG, PNG or WebP image.',
    );

    send(400, 'Bad Request', { detail: 'Use a JPEG, PNG or WebP image.' });
    expect(el.querySelector('#photo-upload-error')?.textContent).toContain(
      'Use a JPEG, PNG or WebP image.',
    );

    send(413, 'Payload Too Large');
    expect(el.querySelector('#photo-upload-error')?.textContent).toContain(
      'The picture must be 10 MB or smaller.',
    );

    send(409, 'Conflict');
    expect(el.querySelector('#photo-upload-error')?.textContent).toContain(
      'You can add up to 10 photos for this event.',
    );

    send(429, 'Too Many Requests', { title: 'Too many requests' }, '42');
    expect(el.querySelector('#photo-upload-error')?.textContent).toContain(
      'Try again in 42 seconds.',
    );

    send(500, 'Server Error');
    expect(el.querySelector('#photo-upload-error')?.textContent).toContain(
      "We couldn't upload your photo. Check your connection and try again.",
    );
    expect(el.querySelector('input[type="file"]')).toBeTruthy();
  });

  it('deletes one of your photos after you confirm, and leaves it when you keep it', async () => {
    const { el, http, flush, button, ownItem, tick } = await render(true);
    await flush([], [waiting, rejected]);

    button('Delete', ownItem('Not approved'))!.click();
    tick();
    expect(ownItem('Not approved').textContent).toContain('Delete this photo?');
    button('Keep it', ownItem('Not approved'))!.click();
    tick();
    expect(ownItem('Not approved').textContent).not.toContain('Delete this photo?');

    button('Delete', ownItem('Not approved'))!.click();
    tick();
    button('Delete', ownItem('Not approved'))!.click();
    const req = http.expectOne('/api/events/e1/photos/p4');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    tick();
    expect(el.textContent).not.toContain('Not approved');
    expect(el.textContent).toContain('Waiting for approval');
  });

  it('says so when a delete fails and keeps the photo', async () => {
    const { el, http, flush, button, ownItem, tick } = await render(true);
    await flush([], [rejected]);
    button('Delete', ownItem('Not approved'))!.click();
    tick();
    button('Delete', ownItem('Not approved'))!.click();
    http
      .expectOne('/api/events/e1/photos/p4')
      .flush(null, { status: 500, statusText: 'Server Error' });
    tick();
    expect(el.textContent).toContain(
      "We couldn't delete your photo. Check your connection and try again.",
    );
    expect(el.textContent).toContain('Not approved');
  });

  it('reports an approved photo that is not yours', async () => {
    const { el, http, flush, button, gridItem, tick } = await render(true);
    await flush([newer, mineApproved], [mineApproved]);

    button('Report', gridItem('Bo')!)!.click();
    tick();
    const dialog = el.querySelector<HTMLDialogElement>('#photo-report')!;
    expect(dialog.open).toBe(true);

    dialog.querySelector('form')!.dispatchEvent(new Event('submit'));
    tick();
    expect(dialog.querySelector('#report-error')?.textContent).toContain('Please choose a reason.');
    http.expectNone('/api/events/e1/photos/p2/reports');

    const reason = dialog.querySelector<HTMLSelectElement>('#report-reason')!;
    reason.value = 'INAPPROPRIATE';
    reason.dispatchEvent(new Event('change'));
    const details = dialog.querySelector<HTMLTextAreaElement>('#report-details')!;
    details.value = '  This is not the venue.  ';
    details.dispatchEvent(new Event('input'));
    dialog.querySelector('form')!.dispatchEvent(new Event('submit'));
    tick();

    const req = http.expectOne('/api/events/e1/photos/p2/reports');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      reason: 'INAPPROPRIATE',
      details: 'This is not the venue.',
    });
    req.flush({}, { status: 201, statusText: 'Created' });
    tick();
    expect(dialog.open).toBe(false);
    expect(el.querySelector('#report-status')?.textContent).toContain('Report sent.');
  });

  it('shows the report rate limit in the dialog', async () => {
    const { el, http, flush, button, gridItem, tick } = await render(true);
    await flush([older], []);
    button('Report', gridItem('Anouk')!)!.click();
    tick();
    const dialog = el.querySelector<HTMLDialogElement>('#photo-report')!;
    const reason = dialog.querySelector<HTMLSelectElement>('#report-reason')!;
    reason.value = 'SPAM';
    reason.dispatchEvent(new Event('change'));
    dialog.querySelector('form')!.dispatchEvent(new Event('submit'));
    tick();

    const req = http.expectOne('/api/events/e1/photos/p1/reports');
    expect(req.request.body).toEqual({ reason: 'SPAM', details: '' });
    req.flush(
      { title: 'Too many requests' },
      { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '15' } },
    );
    tick();
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector('#report-error')?.textContent).toContain(
      'Try again in 15 seconds.',
    );
  });

  it('shows the retryable error when the public photos fail to load', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(EventPhotos);
    fixture.componentRef.setInput('eventId', 'e1');
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http
      .expectOne({ method: 'GET', url: PUBLIC })
      .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')?.textContent).toContain("couldn't load the photos");

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: PUBLIC }).flush([older]);
    await fixture.whenStable();
    expect(el.querySelector('#photo-grid img')?.getAttribute('src')).toBe(older.cardUrl);
  });

  it('shows the retryable error when your own photos fail to load', async () => {
    const { el, http, tick, settle } = await render(true);
    http.expectOne({ method: 'GET', url: PUBLIC }).flush([older]);
    http
      .expectOne({ method: 'GET', url: MINE })
      .flush(null, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(el.textContent).toContain("couldn't load your photos");
    expect(el.querySelector('#photo-grid img')?.getAttribute('src')).toBe(older.cardUrl);

    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    tick();
    http.expectOne({ method: 'GET', url: MINE }).flush([waiting]);
    await settle();
    expect(el.textContent).toContain('Waiting for approval');
  });
});
