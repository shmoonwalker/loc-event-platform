import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { FilterOptions } from '../../../core/models';
import { addDays, amsterdamDate } from '../../../core/time';
import { ListFilters } from '../list-filters';
import { SearchConsole } from './search-console';

@Component({
  imports: [SearchConsole],
  providers: [ListFilters],
  template: '<app-search-console />',
})
class Host {}

const options: FilterOptions = {
  categories: [
    { value: 'music-nightlife', label: 'Music & nightlife' },
    { value: 'sports', label: 'Sports' },
  ],
  tags: [
    { value: 'free', label: 'Free' },
    { value: 'jazz', label: 'Jazz' },
    { value: 'rare-tag', label: 'Rare tag' },
  ],
  maxQueryLength: 100,
} as FilterOptions;

/** A day from now (Amsterdam), so the date range tests never pick a past day. */
const day = (n: number) => addDays(amsterdamDate(), n);

async function open(url = '/') {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([{ path: '', component: Host }]),
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url);
  const http = TestBed.inject(HttpTestingController);
  http.expectOne('/api/events/filter-options').flush(options);
  await harness.fixture.whenStable();
  const el = harness.routeNativeElement!;
  const router = TestBed.inject(Router);
  const settle = () => harness.fixture.whenStable();
  const select = (label: string) =>
    [...el.querySelectorAll('label')]
      .find((l) => l.querySelector('small')?.textContent?.trim() === label)!
      .querySelector('select')!;
  const choose = async (s: HTMLSelectElement, value: string) => {
    s.value = value;
    s.dispatchEvent(new Event('change'));
    await settle();
  };
  const trigger = (label: string) =>
    [...el.querySelectorAll<HTMLButtonElement>('button[aria-expanded]')].find(
      (b) => b.querySelector('small')?.textContent?.trim() === label,
    )!;
  const menu = (key: string) => el.querySelector<HTMLElement>('#menu-' + key);
  const option = (key: string, text: string) =>
    [...menu(key)!.querySelectorAll('label')]
      .find((l) => l.textContent?.trim() === text)!
      .querySelector('input')!;
  return { harness, http, el, router, settle, select, choose, trigger, menu, option };
}

describe('SearchConsole', () => {
  it('picks a date preset from the list and keeps it in the URL', async () => {
    const { router, select, choose } = await open();
    const date = select('Date');
    expect([...date.options].map((o) => o.text.trim())).toEqual([
      'Any date',
      'Today',
      'Tonight',
      'Tomorrow',
      'This weekend',
      'Choose dates',
    ]);

    await choose(date, 'weekend');
    expect(router.url).toBe('/?when=weekend');
  });

  it('shows the date range for "Choose dates" and applies it once both dates are set', async () => {
    const { el, router, settle, select, choose } = await open('/?when=tonight');
    expect(el.querySelector('input[type="date"]')).toBeNull();

    await choose(select('Date'), 'custom');
    expect(router.url).toBe('/');
    const [from, to] = [...el.querySelectorAll<HTMLInputElement>('input[type="date"]')];
    from.value = day(15);
    from.dispatchEvent(new Event('change'));
    to.value = day(17);
    to.dispatchEvent(new Event('change'));
    await settle();

    expect(router.url).toBe(`/?dateFrom=${day(15)}&dateTo=${day(17)}`);
    expect(select('Date').value).toBe('custom');
  });

  it('highlights the Date box only once a date filter is applied', async () => {
    const { el, settle, select, choose } = await open();
    const box = select('Date').closest('label')!;
    expect(box.getAttribute('data-active')).toBe('false');

    await choose(select('Date'), 'custom');
    expect(box.getAttribute('data-active')).toBe('false');
    const [from, to] = [...el.querySelectorAll<HTMLInputElement>('input[type="date"]')];
    from.value = day(15);
    from.dispatchEvent(new Event('change'));
    await settle();
    expect(box.getAttribute('data-active')).toBe('false');

    to.value = day(17);
    to.dispatchEvent(new Event('change'));
    await settle();
    expect(box.getAttribute('data-active')).toBe('true');
  });

  it('goes back to any date when the visitor leaves an unfinished range', async () => {
    const { el, router, settle, select, choose } = await open();
    await choose(select('Date'), 'custom');
    const from = el.querySelector<HTMLInputElement>('input[type="date"]')!;
    from.value = day(15);
    from.dispatchEvent(new Event('change'));
    from.click(); // working inside the range keeps it open
    await settle();
    expect(el.querySelector('input[type="date"]')).not.toBeNull();

    el.querySelector<HTMLElement>('input[type="search"]')!.click();
    await settle();
    expect(el.querySelector('input[type="date"]')).toBeNull();
    expect(select('Date').value).toBe('any');
    expect(select('Date').closest('label')!.getAttribute('data-active')).toBe('false');
    expect(router.url).toBe('/');

    await choose(select('Date'), 'custom');
    expect(el.querySelector<HTMLInputElement>('input[type="date"]')!.value).toBe('');
  });

  it('keeps an applied range open when the visitor clicks elsewhere', async () => {
    const { el, router, settle } = await open(`/?dateFrom=${day(15)}&dateTo=${day(17)}`);
    el.querySelector<HTMLElement>('input[type="search"]')!.click();
    await settle();
    expect(el.querySelectorAll('input[type="date"]').length).toBe(2);
    expect(router.url).toBe(`/?dateFrom=${day(15)}&dateTo=${day(17)}`);
  });

  it('starts the calendars at today and "To" at the chosen "From"', async () => {
    const { el, router, settle, select, choose } = await open();
    await choose(select('Date'), 'custom');
    const [from, to] = [...el.querySelectorAll<HTMLInputElement>('input[type="date"]')];
    expect(from.min).toBe(day(0));
    expect(to.min).toBe(day(0));

    from.value = day(15);
    from.dispatchEvent(new Event('change'));
    await settle();
    expect(to.min).toBe(day(15));

    to.value = day(10); // typed: the calendar would not offer it
    to.dispatchEvent(new Event('change'));
    await settle();
    expect(router.url).toBe('/');
    expect(el.textContent).toContain('“From” must not be after “To”.');
  });

  it('does not apply a typed range that starts in the past', async () => {
    const { el, router, settle, select, choose } = await open();
    await choose(select('Date'), 'custom');
    const [from, to] = [...el.querySelectorAll<HTMLInputElement>('input[type="date"]')];
    from.value = day(-3);
    from.dispatchEvent(new Event('change'));
    to.value = day(2);
    to.dispatchEvent(new Event('change'));
    await settle();

    expect(router.url).toBe('/');
    expect(el.textContent).toContain('Pick today or a later day.');
  });

  it('shows an applied range as readable days in the box and the chip', async () => {
    const { el } = await open('/?dateFrom=2026-11-20&dateTo=2026-11-25');
    const year = amsterdamDate().slice(0, 4);
    const text = year === '2026' ? '20 Nov – 25 Nov' : '20 Nov 2026 – 25 Nov 2026';
    const date = [...el.querySelectorAll('select')][0];
    expect(date.selectedOptions[0].text.trim()).toBe(text);
    expect(el.querySelector(`button[aria-label="Remove ${text}"]`)).not.toBeNull();
  });

  it('offers only the fixed time ranges and no typed time', async () => {
    const { el, router, select, choose } = await open();
    const time = select('Time');
    expect([...time.options].map((o) => o.text.trim())).toEqual([
      'Any time',
      'Morning (06:00 – 12:00)',
      'Afternoon (12:00 – 18:00)',
      'Evening (18:00 – 22:00)',
      'Late night (22:00 – 02:00)',
    ]);
    expect(el.querySelector('input[type="time"]')).toBeNull();

    await choose(time, 'late');
    expect(router.url).toBe('/?timeFrom=22:00&timeTo=02:00');
  });

  it('opens an older link with a typed time as any time', async () => {
    const { select } = await open('/?timeFrom=09:30&timeTo=11:00');
    expect(select('Time').value).toBe('any');
  });

  it('lists every category and keeps the picked ones on the closed menu', async () => {
    const { router, settle, trigger, menu, option } = await open();
    const category = trigger('Category');
    expect(menu('category')).toBeNull();

    category.click();
    await settle();
    expect(category.getAttribute('aria-expanded')).toBe('true');
    option('category', 'Music & nightlife').click();
    await settle();
    option('category', 'Sports').click();
    await settle();

    expect(router.url).toBe('/?category=music-nightlife&category=sports');
    category.click();
    await settle();
    expect(menu('category')).toBeNull();
    expect(category.textContent).toContain('Music & nightlife, Sports');
  });

  it('lists every tag without a "Show all" button', async () => {
    const { el, router, settle, trigger, menu, option } = await open();
    trigger('Tags').click();
    await settle();

    expect(menu('tags')!.querySelectorAll('input[type="checkbox"]').length).toBe(3);
    expect(el.textContent).not.toContain('Show all');
    option('tags', '#rare tag').click();
    await settle();
    expect(router.url).toBe('/?tag=rare-tag');
  });

  it('picks one city and "Anywhere" clears it', async () => {
    const { harness, http, router, settle, trigger, menu, option } = await open();
    trigger('City').click();
    harness.fixture.detectChanges(); // the open City menu asks for cities; the request keeps it unstable
    http.expectOne('/api/cities').flush([
      { slug: 'amsterdam', name: 'Amsterdam' },
      { slug: 'utrecht', name: 'Utrecht' },
    ]);
    await settle();

    expect(menu('city')!.querySelectorAll('input[type="radio"]').length).toBe(3);
    option('city', 'Utrecht').click();
    await settle();
    expect(router.url).toBe('/?city=utrecht');

    option('city', 'Anywhere').click();
    await settle();
    expect(router.url).toBe('/');
  });

  it('closes a menu with Escape and returns focus to its button', async () => {
    const { settle, trigger, menu } = await open();
    const tags = trigger('Tags');
    tags.click();
    await settle();

    menu('tags')!
      .querySelector('input')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(menu('tags')).toBeNull();
    expect(document.activeElement).toBe(tags);
  });

  it('closes a menu on a click outside it', async () => {
    const { el, settle, trigger, menu } = await open();
    trigger('Category').click();
    await settle();

    el.querySelector<HTMLElement>('input[type="search"]')!.click();
    await settle();
    expect(menu('category')).toBeNull();
  });
});
