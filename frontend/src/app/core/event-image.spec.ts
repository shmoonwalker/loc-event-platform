import { CATEGORY_IMAGES, GENERIC_EVENT_IMAGE, categoryImage, eventImageUrl } from './event-image';
import { EventCard } from './models';

const event = (change: Partial<EventCard>): EventCard => ({
  id: 'e1',
  title: 'Session',
  startAt: '2026-10-06T18:30:00Z',
  endAt: '2026-10-06T21:00:00Z',
  place: 'PHYSICAL',
  citySlug: 'amsterdam',
  cityName: 'Amsterdam',
  venueName: 'Bimhuis',
  imageUrl: null,
  category: null,
  ...change,
});

describe('event image fallback', () => {
  it('keeps a real image URL', () => {
    expect(eventImageUrl(event({ imageUrl: 'https://images.example/jazz.jpg', category: 'sports' }))).toBe(
      'https://images.example/jazz.jpg',
    );
  });

  it('treats blank and missing image URLs as absent', () => {
    expect(eventImageUrl(event({ imageUrl: '   ', category: 'sports' }))).toBe(CATEGORY_IMAGES.sports);
    expect(eventImageUrl(event({ imageUrl: undefined, category: 'Arts & Culture' }))).toBe(CATEGORY_IMAGES['arts-culture']);
  });

  it('maps every category slug and its stored label', () => {
    expect(categoryImage('music-nightlife')).toBe(CATEGORY_IMAGES['music-nightlife']);
    expect(categoryImage('Music & Nightlife')).toBe(CATEGORY_IMAGES['music-nightlife']);
    expect(categoryImage('arts-culture')).toBe(CATEGORY_IMAGES['arts-culture']);
    expect(categoryImage('Arts & Culture')).toBe(CATEGORY_IMAGES['arts-culture']);
    expect(categoryImage('sports')).toBe(CATEGORY_IMAGES.sports);
    expect(categoryImage('Sports')).toBe(CATEGORY_IMAGES.sports);
    expect(categoryImage('business-careers')).toBe(CATEGORY_IMAGES['business-careers']);
    expect(categoryImage('Business & Careers')).toBe(CATEGORY_IMAGES['business-careers']);
    expect(categoryImage('technology-science')).toBe(CATEGORY_IMAGES['technology-science']);
    expect(categoryImage('Technology & Science')).toBe(CATEGORY_IMAGES['technology-science']);
    expect(categoryImage('learning-skills')).toBe(CATEGORY_IMAGES['learning-skills']);
    expect(categoryImage('Learning & Skills')).toBe(CATEGORY_IMAGES['learning-skills']);
    expect(categoryImage('nature-sustainability')).toBe(CATEGORY_IMAGES['nature-sustainability']);
    expect(categoryImage('Nature & Sustainability')).toBe(CATEGORY_IMAGES['nature-sustainability']);
    expect(categoryImage('other')).toBe(CATEGORY_IMAGES.other);
    expect(categoryImage('Other')).toBe(CATEGORY_IMAGES.other);
  });

  it('uses the generic image when the category is missing or unknown', () => {
    expect(eventImageUrl(event({ category: null }))).toBe(GENERIC_EVENT_IMAGE);
    expect(eventImageUrl(event({ category: '' }))).toBe(GENERIC_EVENT_IMAGE);
    expect(eventImageUrl(event({ category: 'not-a-category' }))).toBe(GENERIC_EVENT_IMAGE);
  });
});
