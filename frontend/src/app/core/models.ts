// TypeScript mirrors of the backend response records (nl.loc.backend.event.dto.response).
// Interfaces exist only at compile time: they describe the JSON, they do not validate it.

export type Place = 'PHYSICAL' | 'ONLINE';
export type RailMode = 'TONIGHT' | 'WEEKEND' | 'NEAR_YOU' | 'ONLINE';

export interface EventCard {
  id: string;
  title: string;
  startAt: string; // ISO instant, UTC
  endAt: string; // equals startAt when the event has no end
  place: Place;
  citySlug: string | null;
  cityName: string | null;
  venueName: string | null;
  imageUrl: string | null;
}

export interface City {
  slug: string;
  name: string;
}

export interface EventRail {
  mode: RailMode;
  total: number;
  items: EventCard[];
  hasMore: boolean;
  /** Query parameters for GET /api/events that reproduce this rail ("See all"). */
  filters: Record<string, string>;
}

export interface HomeView {
  nearYouCity: City;
  rails: EventRail[];
}

export interface EventPage {
  page: number; // zero-based
  size: number;
  totalElements: number;
  totalPages: number;
  items: EventCard[];
}

/** value is sent to the API, label is shown to people. */
export interface FilterOption {
  value: string;
  label: string;
}

export interface FilterOptions {
  categories: FilterOption[];
  tags: FilterOption[];
  sorts: FilterOption[];
  datePresets: FilterOption[];
  places: FilterOption[];
  timezone: string;
  defaultPageSize: number;
  maxPageSize: number;
  maxQueryLength: number;
}
