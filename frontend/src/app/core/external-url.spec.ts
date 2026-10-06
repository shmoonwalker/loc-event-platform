import { externalUrl } from './external-url';

describe('externalUrl', () => {
  it('keeps http(s) links and adds https:// to bare hosts', () => {
    expect(externalUrl('https://example.nl/a')).toBe('https://example.nl/a');
    expect(externalUrl('www.example.nl')).toBe('https://www.example.nl');
    expect(externalUrl('example.nl:8080/x')).toBe('https://example.nl:8080/x');
  });

  it('refuses other schemes and empty values', () => {
    expect(externalUrl('javascript:alert(1)')).toBeNull();
    expect(externalUrl('mailto:a@b.nl')).toBeNull();
    expect(externalUrl('  ')).toBeNull();
    expect(externalUrl(null)).toBeNull();
  });
});
