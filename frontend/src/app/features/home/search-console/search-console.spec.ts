import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { FilterOptions } from '../../../core/models';
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
    from.value = '2026-12-01';
    from.dispatchEvent(new Event('change'));
    to.value = '2026-12-03';
    to.dispatchEvent(new Event('change'));
    await settle();

    expect(router.url).toBe('/?dateFrom=2026-12-01&dateTo=2026-12-03');
    expect(select('Date').value).toBe('custom');
  });

  it('highlights the Date box only once a date filter is applied', async () => {
    const { el, settle, select, choose } = await open();
    const box = select('Date').closest('label')!;
    expect(box.getAttribute('data-active')).toBe('false');

    await choose(select('Date'), 'custom');
    expect(box.getAttribute('data-active')).toBe('false');
    const [from, to] = [...el.querySelectorAll<HTMLInputElement>('input[type="date"]')];
    from.value = '2026-12-01';
    from.dispatchEvent(new Event('change'));
    await settle();
    expect(box.getAttribute('data-active')).toBe('false');

    to.value = '2026-12-03';
    to.dispatchEvent(new Event('change'));
    await settle();
    expect(box.getAttribute('data-active')).toBe('true');
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
