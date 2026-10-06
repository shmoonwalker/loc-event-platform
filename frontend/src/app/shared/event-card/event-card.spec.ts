import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EventCard } from '../../core/models';
import { EventCardComponent } from './event-card';

const base: EventCard = {
  id: 'e1',
  title: 'Late-night jazz session',
  startAt: '2026-10-06T18:30:00Z',
  endAt: '2026-10-06T21:00:00Z',
  place: 'PHYSICAL',
  citySlug: 'amsterdam',
  cityName: 'Amsterdam',
  venueName: 'Bimhuis',
  imageUrl: null,
  category: 'music-nightlife',
};

function render(event: EventCard): HTMLElement {
  TestBed.configureTestingModule({ providers: [provideRouter([])] });
  const fixture = TestBed.createComponent(EventCardComponent);
  fixture.componentRef.setInput('event', event);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('EventCardComponent', () => {
  it('shows title, Amsterdam time range, venue and city, and links to the event', () => {
    const el = render(base);
    expect(el.textContent).toContain('Late-night jazz session');
    expect(el.textContent).toContain('20:30 – 23:00');
    expect(el.textContent).toContain('Bimhuis, Amsterdam');
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/events/e1');
  });

  it('labels online events and has no venue', () => {
    const el = render({ ...base, place: 'ONLINE', citySlug: null, cityName: null, venueName: null });
    expect(el.textContent).toContain('Online');
    expect(el.textContent).not.toContain('Bimhuis');
  });

  it('shows only the start when the event has no end time', () => {
    const el = render({ ...base, endAt: base.startAt });
    expect(el.textContent).toContain('20:30');
    expect(el.textContent).not.toContain('–');
  });

  it('uses the event image when one is set', () => {
    const el = render({ ...base, imageUrl: 'https://images.example/jazz.jpg', category: 'sports' });
    expect(el.querySelector('img')?.getAttribute('src')).toBe('https://images.example/jazz.jpg');
  });

  it('uses the category image when the event has no image URL', () => {
    const el = render(base);
    expect(el.querySelector('img')?.getAttribute('src')).toBe('/images/categories/music-nightlife.jpg');
  });

  it('treats a blank image URL as missing and uses the category image', () => {
    const el = render({ ...base, imageUrl: '  ', category: 'sports' });
    expect(el.querySelector('img')?.getAttribute('src')).toBe('/images/categories/sports.jpg');
  });

  it('uses the generic image when the category is missing', () => {
    const el = render({ ...base, category: null });
    expect(el.querySelector('img')?.getAttribute('src')).toBe('/images/categories/default.jpg');
  });

  it('uses the generic image when the category is unknown', () => {
    const el = render({ ...base, category: 'nope' });
    expect(el.querySelector('img')?.getAttribute('src')).toBe('/images/categories/default.jpg');
  });
});
