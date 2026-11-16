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

/** Signed in as admin, with the app's real routes. */
async function open(url = '/admin') {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
  });
  const http = TestBed.inject(HttpTestingController);
  TestBed.inject(AuthSession).start('token').subscribe();
  http.expectOne('/api/auth/me').flush(ADMIN);
  const harness = await RouterTestingHarness.create(url);
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
