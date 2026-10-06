import { convertToParamMap } from '@angular/router';
import { EMPTY_FILTERS, Filters, filtersToParams, hasActiveFilters, paramsToFilters } from './filters';

const TODAY = '2026-10-06';
const parse = (params: Record<string, string | string[]>) => paramsToFilters(convertToParamMap(params), TODAY);
const f = (change: Partial<Filters>): Filters => ({ ...EMPTY_FILTERS, ...change });

describe('filters <-> query params', () => {
  it('an empty URL means no filters, and no filters mean an empty URL', () => {
    expect(parse({})).toEqual(EMPTY_FILTERS);
    expect(filtersToParams(EMPTY_FILTERS, TODAY)).toEqual({});
    expect(hasActiveFilters(EMPTY_FILTERS)).toBe(false);
  });

  it('sends tonight and weekend as `when`', () => {
    expect(filtersToParams(f({ date: 'tonight' }), TODAY)).toEqual({ when: 'tonight' });
    expect(filtersToParams(f({ date: 'weekend' }), TODAY)).toEqual({ when: 'weekend' });
  });

  it('turns today/tomorrow into a one-day Amsterdam range', () => {
    expect(filtersToParams(f({ date: 'today' }), TODAY)).toEqual({ dateFrom: TODAY, dateTo: TODAY });
    expect(filtersToParams(f({ date: 'tomorrow' }), TODAY)).toEqual({ dateFrom: '2026-10-07', dateTo: '2026-10-07' });
  });

  it('recognises a one-day range as today/tomorrow and anything else as custom', () => {
    expect(parse({ dateFrom: TODAY, dateTo: TODAY }).date).toBe('today');
    expect(parse({ dateFrom: '2026-10-07', dateTo: '2026-10-07' }).date).toBe('tomorrow');
    expect(parse({ dateFrom: '2026-10-12', dateTo: '2026-10-14' })).toMatchObject({
      date: 'custom',
      dateFrom: '2026-10-12',
      dateTo: '2026-10-14',
    });
  });

  it('does not send half a custom range', () => {
    expect(filtersToParams(f({ date: 'custom', dateFrom: '2026-10-12' }), TODAY)).toEqual({});
    expect(parse({ dateFrom: '2026-10-12' }).date).toBe('any');
  });

  it('maps time presets, including the overnight one', () => {
    expect(filtersToParams(f({ time: 'late' }), TODAY)).toEqual({ timeFrom: '22:00', timeTo: '02:00' });
    expect(parse({ timeFrom: '12:00', timeTo: '18:00' }).time).toBe('afternoon');
    expect(parse({ timeFrom: '09:30', timeTo: '11:00' })).toMatchObject({ time: 'custom', timeFrom: '09:30', timeTo: '11:00' });
  });

  it('repeats category and tag parameters', () => {
    const params = filtersToParams(f({ categories: ['sports', 'other'], tags: ['free', 'outdoor'] }), TODAY);
    expect(params).toEqual({ category: ['sports', 'other'], tag: ['free', 'outdoor'] });
    expect(parse(params)).toMatchObject({ categories: ['sports', 'other'], tags: ['free', 'outdoor'] });
  });

  it('drops the city when the URL says online (the backend would answer 400)', () => {
    expect(parse({ place: 'online', city: 'amsterdam' })).toMatchObject({ place: 'online', city: null });
  });

  it('round-trips a full filter set', () => {
    const full = f({
      q: 'jazz',
      date: 'weekend',
      time: 'evening',
      city: 'utrecht',
      categories: ['music-nightlife'],
      tags: ['live-music'],
      sort: 'relevance',
      page: 2,
    });
    expect(parse(filtersToParams(full, TODAY))).toEqual(full);
  });

  it('reads a rail "See all" link as-is, and "upcoming" means no date filter', () => {
    const rail = parse({ city: 'amsterdam', when: 'upcoming', place: 'physical' });
    expect(rail).toMatchObject({ city: 'amsterdam', date: 'any', place: 'physical' });
    expect(filtersToParams(rail, TODAY)).toEqual({ city: 'amsterdam', place: 'physical' });
  });

  it('ignores garbage page/sort values', () => {
    expect(parse({ page: '-3', sort: 'nonsense' })).toMatchObject({ page: 0, sort: null });
    expect(parse({ page: 'abc' }).page).toBe(0);
  });

  it('omits page 0 and trims the search text', () => {
    expect(filtersToParams(f({ q: '  jazz  ', page: 0 }), TODAY)).toEqual({ q: 'jazz' });
  });
});
