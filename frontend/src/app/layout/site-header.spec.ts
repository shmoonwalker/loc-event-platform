import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CurrentUser } from '../core/auth-api';
import { authGuard } from '../core/auth-guard';
import { AuthSession } from '../core/auth-session';
import { SiteHeader } from './site-header';

@Component({ template: 'Home' })
class HomeStub {}

/**
 * Shows the header as the page itself: on a public page (an event) or on a page for members only,
 * so the tests can see where logout leaves the visitor.
 */
async function open(role: CurrentUser['role'] | null, url = '/events/1') {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([
        { path: 'events/1', component: SiteHeader },
        { path: 'me', component: SiteHeader, canActivate: [authGuard] },
        { path: 'me/settings', component: SiteHeader, canActivate: [authGuard] },
        { path: '', component: HomeStub },
      ]),
    ],
  });
  const session = TestBed.inject(AuthSession);
  const http = TestBed.inject(HttpTestingController);
  if (role) {
    session.start('token').subscribe();
    http.expectOne('/api/auth/me').flush({
      id: 'member-id',
      email: 'shadi@example.com',
      displayName: 'Shadi',
      role,
      avatarUrl: '/avatars/sun.svg',
      avatarPreset: 'SUN',
    } satisfies CurrentUser);
  }
  const harness = await RouterTestingHarness.create(url);
  http.expectOne((req) => req.url === '/api/cities').flush([]);
  harness.detectChanges();

  const el = () => harness.routeNativeElement!;
  const button = (label: string) =>
    [...el().querySelectorAll('button')].find((b) => b.textContent?.trim() === label);
  const loginLink = () =>
    [...el().querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Log in');
  const menuButton = () => el().querySelector<HTMLButtonElement>('button[aria-expanded]');
  const menu = () => el().querySelector('#account-menu');
  const toggleMenu = () => {
    menuButton()!.click();
    harness.detectChanges();
  };

  return { harness, http, session, el, button, loginLink, menuButton, menu, toggleMenu };
}

describe('SiteHeader', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows Sign up and Log in while signed out', async () => {
    const { el, loginLink, menuButton } = await open(null);
    expect(el().querySelector('a[href="/register"]')).not.toBeNull();
    expect(loginLink()).toBeDefined();
    expect(menuButton()).toBeNull();
    expect(el().textContent).not.toContain('Shadi');
  });

  it('shows only the avatar while signed in, with a closed menu', async () => {
    const { el, loginLink, menuButton, menu } = await open('USER');

    expect(menuButton()?.textContent).toContain('Account menu');
    expect(menuButton()?.querySelector('img')?.getAttribute('src')).toBe('/avatars/sun.svg');
    expect(menuButton()?.getAttribute('aria-expanded')).toBe('false');
    expect(menu()).toBeNull();
    expect(el().querySelector('a[href="/register"]')).toBeNull();
    expect(loginLink()).toBeUndefined();
    expect(el().querySelector('a[href="/me"]')).toBeNull();
  });

  it('opens a menu with the account, Account settings, Dashboard and Log out', async () => {
    const { button, menuButton, menu, toggleMenu } = await open('USER');
    toggleMenu();

    expect(menuButton()?.getAttribute('aria-expanded')).toBe('true');
    expect(menuButton()?.getAttribute('aria-controls')).toBe('account-menu');
    expect(menu()?.textContent).toContain('Shadi');
    expect(menu()?.textContent).toContain('Member');
    expect(menu()?.querySelector('a[href="/me/settings"]')?.textContent).toContain(
      'Account settings',
    );
    expect(menu()?.querySelector('a[href="/me"]')?.textContent).toContain('Dashboard');
    expect(button('Log out')).toBeDefined();

    toggleMenu();
    expect(menu()).toBeNull();
  });

  it('shows Admin for an admin account', async () => {
    const { menu, toggleMenu } = await open('ADMIN');
    toggleMenu();
    expect(menu()?.textContent).toContain('Admin');
  });

  it('closes on Escape and gives the focus back to the avatar', async () => {
    const { harness, menuButton, menu, toggleMenu } = await open('USER');
    toggleMenu();
    menu()!
      .querySelector('a')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    harness.detectChanges();

    expect(menu()).toBeNull();
    expect(document.activeElement).toBe(menuButton());
  });

  it('closes on a click outside, not on a click inside', async () => {
    const { harness, el, menu, toggleMenu } = await open('USER');
    toggleMenu();

    menu()!.querySelector('span')!.click();
    harness.detectChanges();
    expect(menu()).not.toBeNull();

    el().querySelector<HTMLElement>('a[aria-label="Loc home"] span')!.click();
    harness.detectChanges();
    expect(menu()).toBeNull();
  });

  it('closes after opening one of its pages', async () => {
    const { harness, http, menu, toggleMenu } = await open('USER');
    toggleMenu();
    menu()!.querySelector<HTMLAnchorElement>('a[href="/me/settings"]')!.click();
    harness.detectChanges();
    expect(menu()).toBeNull();

    // Not whenStable(): it would wait for the cities request of the next page's header.
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/me/settings'));
    http.expectOne((req) => req.url === '/api/cities').flush([]);
  });

  it('follows name and avatar changes without a reload', async () => {
    const { harness, session, menuButton, menu, toggleMenu } = await open('USER');
    session.update({
      ...session.currentUser()!,
      displayName: 'Shadi B',
      avatarUrl: '/avatars/moon.svg',
      avatarPreset: 'MOON',
    });
    harness.detectChanges();
    toggleMenu();

    expect(menuButton()?.querySelector('img')?.getAttribute('src')).toBe('/avatars/moon.svg');
    expect(menu()?.textContent).toContain('Shadi B');
  });

  it('logging out on a public page ends the session and stays on that page', async () => {
    const { harness, http, session, button, toggleMenu, loginLink } = await open('USER');
    toggleMenu();
    button('Log out')!.click();

    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(session.loggedIn()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/events/1');
    expect(loginLink()).toBeDefined();
  });

  it('logging out on a page for members opens the home page', async () => {
    const { harness, http, session, button, toggleMenu } = await open('USER', '/me');
    toggleMenu();
    button('Log out')!.click();

    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(session.loggedIn()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('still logs out in the UI when the server cannot be reached', async () => {
    const { harness, http, session, button, toggleMenu } = await open('USER', '/me');
    toggleMenu();
    button('Log out')!.click();

    http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));
    await harness.fixture.whenStable();

    expect(session.loggedIn()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('removes the account menu when the session ends', async () => {
    const { harness, session, el, menuButton, loginLink } = await open('ADMIN');
    session.end();
    harness.detectChanges();

    expect(menuButton()).toBeNull();
    expect(el().textContent).not.toContain('Shadi');
    expect(loginLink()).toBeDefined();
  });
});
