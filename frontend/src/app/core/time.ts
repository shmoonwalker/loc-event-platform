// The API sends UTC instants; people read Amsterdam time. Intl handles DST for us.
// Formatters are created once because building an Intl.DateTimeFormat is relatively slow.

const TZ = 'Europe/Amsterdam';

const dateParts = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' });
const timeFmt = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const dowFmt = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, weekday: 'short' });
const dayFmt = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, day: 'numeric' });
const monFmt = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, month: 'short' });

const dateFmt = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });

const toDate = (at: Date | string) => (typeof at === 'string' ? new Date(at) : at);

/** The calendar date in Amsterdam as YYYY-MM-DD (the format the API's dateFrom/dateTo use). */
export function amsterdamDate(at: Date | string = new Date()): string {
  const parts = Object.fromEntries(dateParts.formatToParts(toDate(at)).map((p) => [p.type, p.value]));
  return `${parts['year']}-${parts['month']}-${parts['day']}`;
}

/** Add days to a YYYY-MM-DD string. Works on calendar dates only, so DST cannot shift the result. */
export function addDays(ymd: string, days: number): string {
  const [y, m, d] = ymd.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** "20:30" in Amsterdam time. */
export const formatTime = (iso: string): string => timeFmt.format(new Date(iso));

/** Pieces for the card's date badge: { dow: 'Sat', day: '10', mon: 'Oct' }. */
export function dayParts(iso: string): { dow: string; day: string; mon: string } {
  const d = new Date(iso);
  return { dow: dowFmt.format(d), day: dayFmt.format(d), mon: monFmt.format(d) };
}

/** "Sat, 10 Oct 2026" in Amsterdam time. */
export const formatDate = (iso: string): string => dateFmt.format(new Date(iso));

/**
 * "Sat, 10 Oct 2026, 20:30 – 23:00". The end is left out when there is none (end = start)
 * and gets its own date when it falls on a later Amsterdam day.
 */
export function formatWhen(startAt: string, endAt: string): string {
  const start = `${formatDate(startAt)}, ${formatTime(startAt)}`;
  if (startAt === endAt) return start;
  const end = amsterdamDate(startAt) === amsterdamDate(endAt) ? formatTime(endAt) : `${formatDate(endAt)}, ${formatTime(endAt)}`;
  return `${start} – ${end}`;
}
