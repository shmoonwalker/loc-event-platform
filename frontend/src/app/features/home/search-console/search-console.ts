import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { EventsApi } from '../../../core/events-api';
import { DATE_PRESETS, FILTER_RESET, Filters, TIME_PRESETS } from '../../../core/filters';
import { FilterOption } from '../../../core/models';
import { valueOf } from '../../../core/resource';
import { IconComponent } from '../../../shared/icon/icon';
import { ListFilters } from '../list-filters';

type Panel = 'date' | 'time' | 'city' | 'category' | 'tags';

interface Trigger {
  key: Panel;
  label: string;
  value: string;
  active: boolean;
}

/** A removable chip under the console. `remove` is the filter change that undoes it. */
interface Token {
  key: string;
  label: string;
  remove: Partial<Filters>;
}

const POPULAR_TAGS = [
  'free', 'live-music', 'outdoor', 'family-friendly', 'workshop', 'meetup', 'comedy', 'festival', 'exhibition',
  'food-and-drink', 'techno', 'jazz', 'beginners', 'students', 'networking', 'theatre', 'kids', 'wellness',
];

const toggled = (list: string[], value: string) => (list.includes(value) ? list.filter((v) => v !== value) : [...list, value]);
const summary = (values: string[], label: (v: string) => string, many: string, none: string) =>
  values.length === 0 ? none : values.length === 1 ? label(values[0]) : `${values.length} ${many}`;

/**
 * Search box, filter triggers with drawers (date, time, city, category, tags), the online switch and the
 * applied-filter chips. It holds NO filter state of its own: every click calls store.patch(), which writes
 * the URL; the new filters flow back in through store.filters(). Only drafts (typed-but-not-applied text) live here.
 */
@Component({
  selector: 'app-search-console',
  imports: [RouterLink, IconComponent],
  templateUrl: './search-console.html',
})
export class SearchConsole {
  protected readonly store = inject(ListFilters);
  private readonly api = inject(EventsApi);
  protected readonly f = this.store.filters;

  protected readonly datePresets = DATE_PRESETS;
  protected readonly timePresets = TIME_PRESETS;
  protected readonly open = signal<Panel | null>('date');

  /** Categories and tags (value + label) from the backend; loaded once. */
  private readonly optionsResource = rxResource({ stream: () => this.api.filterOptions() });
  protected readonly options = computed(() => valueOf(this.optionsResource));
  protected readonly optionsLoading = this.optionsResource.isLoading;

  // ----- drafts -----
  // linkedSignal = a writable signal that resets to its computation whenever the filters change.
  // So the box shows what the URL says, but you can type freely until you submit.
  protected readonly query = linkedSignal(() => this.f().q);
  protected readonly dateDraft = linkedSignal(() => ({ from: this.f().dateFrom, to: this.f().dateTo }));
  protected readonly timeDraft = linkedSignal(() => ({ from: this.f().timeFrom, to: this.f().timeTo }));
  protected readonly dateError = computed(() => !!this.dateDraft().from && !!this.dateDraft().to && this.dateDraft().from > this.dateDraft().to);
  protected readonly timeError = computed(() => !!this.timeDraft().from && this.timeDraft().from === this.timeDraft().to);

  // ----- cities: server-side search, debounced, only while the City drawer is open -----
  protected readonly cityText = signal('');
  private readonly cityQuery = toSignal(toObservable(this.cityText).pipe(debounceTime(250), distinctUntilChanged()), { initialValue: '' });
  private readonly citiesResource = rxResource({
    params: () => (this.open() === 'city' ? this.cityQuery() : undefined), // undefined = stay idle
    stream: ({ params }) => this.api.cities(params),
  });
  protected readonly cityList = computed(() => valueOf(this.citiesResource) ?? []);
  protected readonly citiesLoading = this.citiesResource.isLoading;

  // ----- tags -----
  protected readonly showAllTags = signal(false);
  protected readonly tagsShown = computed<FilterOption[]>(() => {
    const all = this.options()?.tags ?? [];
    if (this.showAllTags()) return all;
    return POPULAR_TAGS.map((v) => all.find((t) => t.value === v)).filter((t): t is FilterOption => !!t);
  });

  // ----- derived text for the triggers and tokens -----
  private categoryLabel = (v: string) => this.options()?.categories.find((c) => c.value === v)?.label ?? v;
  private tagLabel = (v: string) => this.options()?.tags.find((t) => t.value === v)?.label ?? v;
  private cityLabel = (slug: string) => this.cityList().find((c) => c.slug === slug)?.name ?? slug.charAt(0).toUpperCase() + slug.slice(1);

  protected readonly dateText = computed(() => {
    const f = this.f();
    return f.date === 'custom' ? `${f.dateFrom} to ${f.dateTo}` : (DATE_PRESETS.find((p) => p.value === f.date)?.label ?? '');
  });
  protected readonly timeText = computed(() => {
    const f = this.f();
    return f.time === 'custom' ? `${f.timeFrom} – ${f.timeTo}` : (TIME_PRESETS.find((p) => p.value === f.time)?.label ?? '');
  });

  protected readonly triggers = computed<Trigger[]>(() => {
    const f = this.f();
    return [
      { key: 'date', label: 'Date', value: this.dateText(), active: f.date !== 'any' },
      { key: 'time', label: 'Time', value: this.timeText(), active: f.time !== 'any' },
      {
        key: 'city',
        label: 'City',
        value: f.place === 'online' ? 'Online only' : f.city ? this.cityLabel(f.city) : 'Anywhere',
        active: !!f.city,
      },
      { key: 'category', label: 'Category', value: summary(f.categories, this.categoryLabel, 'categories', 'All'), active: f.categories.length > 0 },
      { key: 'tags', label: 'Tags', value: summary(f.tags, this.tagLabel, 'tags', 'Any'), active: f.tags.length > 0 },
    ];
  });

  protected readonly tokens = computed<Token[]>(() => {
    const f = this.f();
    const t: Token[] = [];
    if (f.q.trim()) t.push({ key: 'q', label: `“${f.q.trim()}”`, remove: { q: '', sort: null } });
    if (f.date !== 'any') t.push({ key: 'date', label: this.dateText(), remove: { date: 'any', dateFrom: '', dateTo: '' } });
    if (f.time !== 'any') t.push({ key: 'time', label: this.timeText(), remove: { time: 'any', timeFrom: '', timeTo: '' } });
    if (f.place) t.push({ key: 'place', label: f.place === 'online' ? 'Online only' : 'In person only', remove: { place: null } });
    if (f.city) t.push({ key: 'city', label: this.cityLabel(f.city), remove: { city: null } });
    for (const c of f.categories) t.push({ key: `cat:${c}`, label: this.categoryLabel(c), remove: { categories: f.categories.filter((v) => v !== c) } });
    for (const g of f.tags) t.push({ key: `tag:${g}`, label: '#' + this.tagLabel(g).toLowerCase(), remove: { tags: f.tags.filter((v) => v !== g) } });
    return t;
  });

  // ----- actions -----
  protected togglePanel(panel: Panel): void {
    this.open.update((current) => (current === panel ? null : panel));
  }

  protected search(event: Event): void {
    event.preventDefault();
    const q = this.query().trim();
    // Without search text "best match" means nothing, so fall back to the default order.
    this.store.patch(q ? { q } : { q, sort: null }, { push: true, fragment: 'all' });
  }

  protected setDate(which: 'from' | 'to', value: string): void {
    this.dateDraft.update((d) => ({ ...d, [which]: value }));
    const { from, to } = this.dateDraft();
    if (from && to && from <= to) this.store.patch({ date: 'custom', dateFrom: from, dateTo: to });
  }

  protected setTime(which: 'from' | 'to', value: string): void {
    this.timeDraft.update((d) => ({ ...d, [which]: value }));
    const { from, to } = this.timeDraft();
    if (from && to && from !== to) this.store.patch({ time: 'custom', timeFrom: from, timeTo: to });
  }

  protected setOnline(on: boolean): void {
    this.store.patch(on ? { place: 'online', city: null } : { place: null }); // the backend rejects online + city
  }

  protected pickCity(slug: string): void {
    this.store.patch({ city: this.f().city === slug ? null : slug });
  }

  protected toggleCategory(value: string): void {
    this.store.patch({ categories: toggled(this.f().categories, value) });
  }

  protected toggleTag(value: string): void {
    this.store.patch({ tags: toggled(this.f().tags, value) });
  }

  protected clearAll(): void {
    this.query.set('');
    this.store.patch(FILTER_RESET);
  }
}
