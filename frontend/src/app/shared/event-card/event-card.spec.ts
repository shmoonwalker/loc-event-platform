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
});
