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
  /**
   * Category slug or label, when the payload includes one.
   * Omitted or blank means the image fallback uses the generic default.
   */
  category?: string | null;
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

export interface OrganizerInfo {
  id: string;
  name: string;
  description: string | null;
  site: string | null;
}

export interface Occurrence {
  startAt: string; // ISO instant, UTC
  endAt: string; // equals startAt when the event has no end
}

/** Forecast for the event's first date. weatherCode is a WMO code. */
export interface Weather {
  forecastFetchedAt: string;
  forecastHour: string;
  temperatureCelsius: number | null;
  precipitationProbabilityPercent: number | null;
  windSpeedKmh: number | null;
  weatherCode: number | null;
}

/** The event page. Online events have null venue, address, city, coordinates and weather. */
export interface EventDetail {
  id: string;
  title: string;
  description: string | null; // plain text
  place: Place;
  sourceUrl: string | null;
  venueName: string | null;
  address: string | null;
  postalCode: string | null;
  citySlug: string | null;
  cityName: string | null;
  latitude: number | null;
  longitude: number | null;
  categories: FilterOption[];
  tags: FilterOption[];
  imageUrls: string[]; // the first is the main image
  organizers: OrganizerInfo[];
  occurrences: Occurrence[]; // soonest first, never empty
  weather: Weather | null;
}

export interface OrganizerDetail {
  organizer: OrganizerInfo;
  events: EventPage;
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
