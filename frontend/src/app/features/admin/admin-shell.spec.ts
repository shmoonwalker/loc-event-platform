import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { CurrentUser } from '../../core/auth-api';
import { adminGuard } from '../../core/auth-guard';
import { AuthSession } from '../../core/auth-session';

const ADMIN: CurrentUser = {
  id: 'admin-id',
  email: 'admin@example.com',
  displayName: 'Shadi',
  role: 'ADMIN',
  avatarUrl: '/avatars/sun.svg',
  avatarPreset: 'SUN',
};

const REPORT = {
  targetType: 'EVENT',
  targetId: 'e1',
  openCount: 2,
  reasons: ['SPAM'],
  reportIds: ['r1', 'r2'],
};
const REPORT_2 = { ...REPORT, targetType: 'COMMENT', targetId: 'c1' };

/** Signed in as admin, with the app's real routes. */
async function open(url = '/admin') {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
  });
  const http = TestBed.inject(HttpTestingController);
  TestBed.inject(AuthSession).start('token').subscribe();
  http.expectOne('/api/auth/me').flush(ADMIN);
  const harness = await RouterTestingHarness.create(url);
  // The overview counts what waits in each section.
  http.match('/api/admin/reports').forEach((req) => req.flush([REPORT, REPORT_2]));
  http.match('/api/admin/hidden-events').forEach((req) => req.flush([]));
  await harness.fixture.whenStable();
  const el = harness.routeNativeElement!;
  const nav = () => el.querySelector<HTMLElement>('#admin-nav')!;
  const link = (label: string) =>
    [...nav().querySelectorAll('a')].find((a) => a.textContent?.trim() === label)!;
  return { harness, http, el, nav, link };
}

it('keeps /admin and everything under it for admins', () => {
  expect(routes.find((r) => r.path === 'admin')?.canActivate).toEqual([adminGuard]);
});

describe('AdminShell', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('opens the overview in its own layout, without the member header', async () => {
    const { el, link } = await open();

    expect(el.querySelector('h1')?.textContent).toContain('Overview');
    expect(el.textContent).toContain('Signed in as Shadi');
    expect(el.querySelector('nav[aria-label="Admin sections"]')).not.toBeNull();
    expect(link('Overview').getAttribute('aria-current')).toBe('page');
    expect(link('← Back to site').getAttribute('href')).toBe('/');
    expect(el.querySelector('app-site-header')).toBeNull();
    expect(el.textContent).toContain('Admin');
  });

  it('lists every section in the navigation and as an overview card with its count', async () => {
    const { el, link } = await open();

    expect(link('Reports').getAttribute('href')).toBe('/admin/reports');
    expect(link('Hidden events').getAttribute('href')).toBe('/admin/hidden-events');
    const card = (label: string) =>
      [...el.querySelectorAll('main a')].find((a) => a.textContent?.includes(label))!;
    expect(card('Reports').getAttribute('href')).toBe('/admin/reports');
    expect(card('Reports').textContent).toContain('2');
    expect(card('Hidden events').getAttribute('href')).toBe('/admin/hidden-events');
    expect(card('Hidden events').textContent).not.toMatch(/\d/);
  });

  it('marks the open section in the navigation', async () => {
    const { link } = await open('/admin/hidden-events');
    expect(link('Hidden events').getAttribute('aria-current')).toBe('page');
    expect(link('Overview').getAttribute('aria-current')).toBeNull();
  });

  it('opens the overview for an unknown admin page', async () => {
    await open('/admin/nothing-here');
    expect(TestBed.inject(Router).url).toBe('/admin');
  });

  it('folds the sections behind a button on small screens', async () => {
    const { harness, el, nav, link } = await open();
    const button = el.querySelector<HTMLButtonElement>('button[aria-controls="admin-nav"]')!;

    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(nav().classList).toContain('hidden');

    button.click();
    harness.detectChanges();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(nav().classList).not.toContain('hidden');

    link('Overview').click();
    await harness.fixture.whenStable();
    expect(button.getAttribute('aria-expanded')).toBe('false');
  });
});
