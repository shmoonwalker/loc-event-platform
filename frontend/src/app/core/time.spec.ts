import { addDays, amsterdamDate, dayParts, formatTime, formatWhen } from './time';

describe('time (Europe/Amsterdam)', () => {
  it('formats a UTC instant as Amsterdam wall-clock time (summer, UTC+2)', () => {
    expect(formatTime('2026-10-06T18:30:00Z')).toBe('20:30');
  });

  it('formats in winter (UTC+1)', () => {
    expect(formatTime('2026-12-01T18:30:00Z')).toBe('19:30');
  });

  it('uses 00:xx, never 24:xx, after midnight', () => {
    expect(formatTime('2026-10-06T22:05:00Z')).toBe('00:05');
  });

  it('moves to the next Amsterdam day when UTC is still the previous day', () => {
    expect(amsterdamDate('2026-10-06T22:30:00Z')).toBe('2026-10-07');
    expect(amsterdamDate('2026-12-31T23:30:00Z')).toBe('2027-01-01');
  });

  it('splits the date badge parts', () => {
    expect(dayParts('2026-10-10T08:00:00Z')).toEqual({ dow: 'Sat', day: '10', mon: 'Oct' });
  });

  it('adds days across month and year ends', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('formats an occurrence, with the end date only when it is another day', () => {
    expect(formatWhen('2026-10-10T18:30:00Z', '2026-10-10T21:00:00Z')).toBe('Sat, 10 Oct 2026, 20:30 – 23:00');
    expect(formatWhen('2026-10-10T18:30:00Z', '2026-10-10T18:30:00Z')).toBe('Sat, 10 Oct 2026, 20:30');
    expect(formatWhen('2026-10-10T20:00:00Z', '2026-10-11T00:30:00Z')).toBe('Sat, 10 Oct 2026, 22:00 – Sun, 11 Oct 2026, 02:30');
  });
});
