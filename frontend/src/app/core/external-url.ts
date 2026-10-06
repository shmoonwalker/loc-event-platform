/**
 * A link from the source data, made safe for href: "www.example.nl" gets https://, and any other
 * scheme (javascript:, mailto:, ...) gives null so the caller shows no link.
 */
export function externalUrl(url: string | null): string | null {
  const u = url?.trim();
  if (!u) return null;
  if (/^https?:\/\//i.test(u)) return u;
  return /^[a-z][\w+.-]*:(?!\d)/i.test(u) ? null : `https://${u}`;
}
