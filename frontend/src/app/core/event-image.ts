import { EventCard } from './models';

/** Slugs match {@code EventCategory} on the backend. Paths are served from frontend/public. */
export const CATEGORY_IMAGES = {
  'music-nightlife': '/images/categories/music-nightlife.jpg',
  'arts-culture': '/images/categories/arts-culture.jpg',
  sports: '/images/categories/sports.jpg',
  'business-careers': '/images/categories/business-careers.jpg',
  'technology-science': '/images/categories/technology-science.jpg',
  'learning-skills': '/images/categories/learning-skills.jpg',
  'nature-sustainability': '/images/categories/nature-sustainability.jpg',
  other: '/images/categories/other.jpg',
} as const;

export type EventCategorySlug = keyof typeof CATEGORY_IMAGES;

/** Used when an event has no image and no known category. */
export const GENERIC_EVENT_IMAGE = '/images/categories/default.jpg';

const LABEL_TO_SLUG: Record<string, EventCategorySlug> = {
  'music & nightlife': 'music-nightlife',
  'arts & culture': 'arts-culture',
  sports: 'sports',
  'business & careers': 'business-careers',
  'technology & science': 'technology-science',
  'learning & skills': 'learning-skills',
  'nature & sustainability': 'nature-sustainability',
  other: 'other',
};

export function categorySlug(value: string | null | undefined): EventCategorySlug | null {
  if (value == null) return null;
  const key = value.trim().toLowerCase();
  if (!key) return null;
  if (Object.hasOwn(CATEGORY_IMAGES, key)) return key as EventCategorySlug;
  return LABEL_TO_SLUG[key] ?? null;
}

export function categoryImage(category: string | null | undefined): string {
  const slug = categorySlug(category);
  return slug ? CATEGORY_IMAGES[slug] : GENERIC_EVENT_IMAGE;
}

/** Real image URL when present; otherwise the category image, or the generic default. */
export function eventImageUrl(event: Pick<EventCard, 'imageUrl' | 'category'>): string {
  const url = event.imageUrl?.trim();
  if (url) return url;
  return categoryImage(event.category);
}
