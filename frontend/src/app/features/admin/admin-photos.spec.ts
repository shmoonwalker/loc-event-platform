import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminPhoto } from '../../core/admin-api';
import { AdminPhotos } from './admin-photos';

const PENDING: AdminPhoto = {
  id: 'p1',
  eventId: 'e1',
  eventTitle: 'Jazz night',
  status: 'PENDING',
  cardUrl: '/photos/p1/card.jpg',
  fullUrl: '/photos/p1/full.jpg',
  createdAt: '2026-11-16T20:00:00Z',
  uploader: { displayName: 'Anouk' },
};
const APPROVED: AdminPhoto = {
  ...PENDING,
  id: 'p2',
  status: 'APPROVED',
  eventTitle: 'Market day',
  uploader: { displayName: 'Bram' },
};

function setup() {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
}

async function render(list: AdminPhoto[] = [PENDING]) {
  setup();
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(AdminPhotos);
  fixture.detectChanges();
  http.expectOne('/api/admin/photos?status=PENDING').flush(list);
  await fixture.whenStable();

  const el = fixture.nativeElement as HTMLElement;
  const tick = () => fixture.detectChanges();
  const row = (text: string) =>
    [...el.querySelectorAll('li')].find((li) => li.textContent?.includes(text))!;
  const button = (label: string, within: ParentNode = el) =>
    [...within.querySelectorAll('button')].find((b) => b.textContent?.trim() === label);
  return { el, fixture, http, tick, row, button };
}

describe('AdminPhotos', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows a pending photo with its event, uploader and review actions', async () => {
    const { el, row, button } = await render();

    expect(el.querySelector('h1')?.textContent).toContain('Event photos');
    expect(row('Jazz night').textContent).toContain('Pending');
    expect(row('Jazz night').textContent).toContain('Anouk');
    expect(row('Jazz night').textContent).toContain('Mon, 16 Nov 2026, 21:00');
    expect(row('Jazz night').querySelector('a')?.getAttribute('href')).toBe('/events/e1');
    expect(row('Jazz night').querySelector('img')?.getAttribute('src')).toBe('/photos/p1/card.jpg');
    expect(button('Approve', row('Jazz night'))).toBeDefined();
    expect(button('Reject', row('Jazz night'))).toBeDefined();
    expect(button('Remove', row('Jazz night'))).toBeUndefined();
  });

  it('says when nothing is waiting', async () => {
    const { el } = await render([]);
    expect(el.textContent).toContain('No photos waiting');
  });

  it('shows the event id when the photo has no title', async () => {
    const { row } = await render([{ ...PENDING, eventTitle: undefined }]);
    expect(row('Event').textContent).toContain('e1');
    expect(row('Event').querySelector('a')).toBeNull();
  });

  it('offers a retry when the list fails to load', async () => {
    setup();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AdminPhotos);
    fixture.detectChanges();
    http.expectOne('/api/admin/photos?status=PENDING').error(new ProgressEvent('error'));
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')?.textContent).toContain("couldn't load the photos");
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Try again'))!.click();
    fixture.detectChanges();
    http.expectOne('/api/admin/photos?status=PENDING').flush([PENDING]);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Jazz night');
  });

  it('approves a photo and loads the pending list again', async () => {
    const { fixture, http, tick, row, button } = await render();
    button('Approve', row('Jazz night'))!.click();

    const req = http.expectOne('/api/admin/photos/p1/approve');
    expect(req.request.method).toBe('POST');
    req.flush({ ...PENDING, status: 'APPROVED' });
    tick();
    http.expectOne('/api/admin/photos?status=PENDING').flush([]);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('No photos waiting');
  });

  it('shows a failed approve on its row', async () => {
    const { http, tick, row, button } = await render();
    button('Approve', row('Jazz night'))!.click();
    http.expectOne('/api/admin/photos/p1/approve').error(new ProgressEvent('error'));
    tick();

    expect(row('Jazz night').querySelector('[role="alert"]')?.textContent).toContain(
      "couldn't approve this photo",
    );
  });

  it('lists approved photos and removes one after a confirmation', async () => {
    const { el, fixture, http, tick, row, button } = await render();
    el.querySelectorAll('button')[1].click();
    tick();
    http.expectOne('/api/admin/photos?status=APPROVED').flush([APPROVED]);
    await fixture.whenStable();

    expect(row('Market day').textContent).toContain('Approved');
    expect(button('Approve', row('Market day'))).toBeUndefined();
    button('Remove', row('Market day'))!.click();
    tick();
    expect(row('Market day').textContent).toContain('Remove this photo?');

    button('Remove', row('Market day'))!.click();
    const req = http.expectOne('/api/admin/photos/p2');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    tick();
    http.expectOne('/api/admin/photos?status=APPROVED').flush([]);
    await fixture.whenStable();

    expect(el.textContent).toContain('No approved photos');
  });
});
