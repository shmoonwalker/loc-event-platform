/**
 * Which page buttons to show for a (possibly long) list: always the first, the last and the current
 * page with its neighbours; `null` marks a gap ("…"). Pages are zero-based.
 *   (5, 20) -> [0, null, 4, 5, 6, null, 19]
 */
export function pageWindow(current: number, total: number): (number | null)[] {
  const wanted = [...new Set([0, total - 1, current - 1, current, current + 1])]
    .filter((p) => p >= 0 && p < total)
    .sort((a, b) => a - b);
  const out: (number | null)[] = [];
  wanted.forEach((p, i) => {
    const prev = wanted[i - 1];
    if (i > 0 && p - prev === 2) out.push(prev + 1); // a one-page gap is shown, not hidden behind "…"
    else if (i > 0 && p - prev > 2) out.push(null);
    out.push(p);
  });
  return out;
}
