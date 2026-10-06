import { ParamMap } from '@angular/router';
import { QueryParams } from './events-api';
import { addDays } from './time';

// The list's filter state. The URL query string is the source of truth, so a filtered list
// can be bookmarked, shared and survives refresh. These two pure functions convert between
// the URL (which uses the backend's own parameter names) and a friendlier Filters object.
// Pure functions are trivial to unit test: see filters.spec.ts.

export type DatePreset = 'any' | 'today' | 'tonight' | 'tomorrow' | 'weekend' | 'custom';
export type TimePreset = 'any' | 'morning' | 'afternoon' | 'evening' | 'late' | 'custom';
export type PlaceFilter = 'online' | 'physical';
export type SortKey = 'start_time' | 'relevance';

export interface Filters {
  q: string;
  date: DatePreset;
  dateFrom: string; // only used when date === 'custom'
  dateTo: string;
  time: TimePreset;
  timeFrom: string; // only used when time === 'custom'
  timeTo: string;
  place: PlaceFilter | null;
  city: string | null; // the backend accepts a single city
  categories: string[]; // OR
  tags: string[]; // AND
  sort: SortKey | null; // null = backend default (relevance with search text, else start time)
  page: number; // zero-based, like the backend
}

export const DATE_PRESETS: { value: Exclude<DatePreset, 'custom'>; label: string }[] = [
  { value: 'any', label: 'Any date' },
  { value: 'today', label: 'Today' },
  { value: 'tonight', label: 'Tonight' },
  { value: 'tomorrow', label: 'Tomorrow' },
  { value: 'weekend', label: 'This weekend' },
];

export const TIME_PRESETS: {
  value: Exclude<TimePreset, 'custom'>;
  label: string;
  hours: string;
  from: string | null;
  to: string | null;
}[] = [
  { value: 'any', label: 'Any time', hours: 'All day', from: null, to: null },
  { value: 'morning', label: 'Morning', hours: '06:00 – 12:00', from: '06:00', to: '12:00' },
  { value: 'afternoon', label: 'Afternoon', hours: '12:00 – 18:00', from: '12:00', to: '18:00' },
  { value: 'evening', label: 'Evening', hours: '18:00 – 22:00', from: '18:00', to: '22:00' },
  { value: 'late', label: 'Late night', hours: '22:00 – 02:00', from: '22:00', to: '02:00' },
];

export const SORTS: { value: SortKey; label: string }[] = [
  { value: 'start_time', label: 'Soonest first' },
  { value: 'relevance', label: 'Best match' },
];

export const EMPTY_FILTERS: Filters = {
  q: '',
  date: 'any',
  dateFrom: '',
  dateTo: '',
  time: 'any',
  timeFrom: '',
  timeTo: '',
  place: null,
  city: null,
  categories: [],
  tags: [],
  sort: null,
  page: 0,
};

/** Back to the bare list ("Clear all"). */
export const FILTER_RESET: Partial<Filters> = { ...EMPTY_FILTERS };

export function hasActiveFilters(f: Filters): boolean {
  return (
    f.q.trim() !== '' ||
    f.date !== 'any' ||
    f.time !== 'any' ||
    f.place !== null ||
    f.city !== null ||
    f.categories.length > 0 ||
    f.tags.length > 0
  );
}

/** Filters -> backend query parameters. `today` is the Amsterdam date (passed in so tests are deterministic). */
export function filtersToParams(f: Filters, today: string): QueryParams {
  const p: QueryParams = {};
  const q = f.q.trim();
  if (q) p['q'] = q;

  if (f.date === 'tonight' || f.date === 'weekend') {
    p['when'] = f.date;
  } else if (f.date === 'today') {
    p['dateFrom'] = p['dateTo'] = today;
  } else if (f.date === 'tomorrow') {
    p['dateFrom'] = p['dateTo'] = addDays(today, 1);
  } else if (f.date === 'custom' && f.dateFrom && f.dateTo) {
    p['dateFrom'] = f.dateFrom;
    p['dateTo'] = f.dateTo;
  }

  const preset = TIME_PRESETS.find((t) => t.value === f.time);
  if (preset?.from && preset.to) {
    p['timeFrom'] = preset.from;
    p['timeTo'] = preset.to;
  } else if (f.time === 'custom' && f.timeFrom && f.timeTo) {
    p['timeFrom'] = f.timeFrom;
    p['timeTo'] = f.timeTo;
  }

  if (f.place) p['place'] = f.place;
  if (f.city) p['city'] = f.city;
  if (f.categories.length) p['category'] = f.categories;
  if (f.tags.length) p['tag'] = f.tags;
  if (f.sort) p['sort'] = f.sort;
  if (f.page > 0) p['page'] = String(f.page);
  return p;
}

/** Backend query parameters (the URL) -> Filters. Unknown or incomplete values fall back to "no filter". */
export function paramsToFilters(p: ParamMap, today: string): Filters {
  const when = p.get('when');
  const dateFrom = p.get('dateFrom');
  const dateTo = p.get('dateTo');
  let date: DatePreset = 'any';
  let from = '';
  let to = '';
  if (when === 'tonight' || when === 'weekend') {
    date = when; // 'upcoming' (used by the rails) is the default anyway, so it maps to 'any'
  } else if (dateFrom && dateTo) {
    if (dateFrom === today && dateTo === today) date = 'today';
    else if (dateFrom === addDays(today, 1) && dateTo === addDays(today, 1)) date = 'tomorrow';
    else {
      date = 'custom';
      from = dateFrom;
      to = dateTo;
    }
  }

  const timeFrom = p.get('timeFrom');
  const timeTo = p.get('timeTo');
  let time: TimePreset = 'any';
  let tFrom = '';
  let tTo = '';
  if (timeFrom && timeTo) {
    const preset = TIME_PRESETS.find((t) => t.from === timeFrom && t.to === timeTo);
    if (preset) time = preset.value;
    else {
      time = 'custom';
      tFrom = timeFrom;
      tTo = timeTo;
    }
  }

  const rawPlace = p.get('place')?.toLowerCase();
  const place: PlaceFilter | null = rawPlace === 'online' || rawPlace === 'physical' ? rawPlace : null;
  const rawSort = p.get('sort');
  const sort: SortKey | null = rawSort === 'start_time' || rawSort === 'relevance' ? rawSort : null;

  return {
    q: p.get('q') ?? '',
    date,
    dateFrom: from,
    dateTo: to,
    time,
    timeFrom: tFrom,
    timeTo: tTo,
    place,
    city: place === 'online' ? null : p.get('city'), // the backend rejects online + city
    categories: p.getAll('category'),
    tags: p.getAll('tag'),
    sort,
    page: Math.max(0, Number.parseInt(p.get('page') ?? '', 10) || 0),
  };
}
