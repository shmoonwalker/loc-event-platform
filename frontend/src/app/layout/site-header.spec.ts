import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CurrentUser } from '../core/auth-api';
import { AuthSession } from '../core/auth-session';
import { SiteHeader } from './site-header';

@Component({ template: 'Home' })
class HomeStub {}

/** Shows the header on a page that is not home, so the test can see logout going back to "/". */
async function open(role: CurrentUser['role'] | null) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([
        { path: 'events/1', component: SiteHeader },
        { path: '', component: HomeStub },
      ]),
    ],
  });
  const session = TestBed.inject(AuthSession);
  const http = TestBed.inject(HttpTestingController);
  if (role) {
    session.start('token').subscribe();
    http
      .expectOne('/api/auth/me')
      .flush({ id: 'member-id', email: 'shadi@example.com', displayName: 'Shadi', role });
  }
  const harness = await RouterTestingHarness.create('/events/1');
  http.expectOne((req) => req.url === '/api/cities').flush([]);
  harness.detectChanges();

  const el = harness.routeNativeElement!;
  const logoutButton = () =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Log out');
  const loginLink = () =>
    [...el.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Log in');

  return { harness, http, session, el, logoutButton, loginLink };
}

describe('SiteHeader', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows Log in while signed out', async () => {
    const { el, logoutButton, loginLink } = await open(null);
    expect(el.querySelector('a[href="/register"]')).not.toBeNull();
    expect(el.textContent).not.toContain('Shadi');
    expect(el.textContent).not.toContain('Admin');

    expect(loginLink()).toBeDefined();
    expect(logoutButton()).toBeUndefined();
    expect(el.querySelector('a[href="/saved"]')).toBeNull();
  });

  it('shows Log out instead of Log in while signed in', async () => {
    const { el, logoutButton, loginLink } = await open('USER');
    expect(el.textContent).toContain('Shadi');
    expect(el.textContent).toContain('Member');
    expect(el.textContent).not.toContain('Admin');
    expect(el.querySelector('a[href="/register"]')).toBeNull();

    expect(logoutButton()).toBeDefined();
    expect(loginLink()).toBeUndefined();
    expect(el.querySelector('a[href="/saved"]')?.textContent).toContain('Saved');
  });

  it('logout ends the session on the server and in the UI, then opens the home page', async () => {
    const { harness, http, session, logoutButton } = await open('USER');
    logoutButton()!.click();

    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(session.loggedIn()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('still logs out in the UI when the server cannot be reached', async () => {
    const { harness, http, session, logoutButton } = await open('USER');
    logoutButton()!.click();

    http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));
    await harness.fixture.whenStable();

    expect(session.loggedIn()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('shows an admin account with the shared member action', async () => {
    const { el, logoutButton, loginLink } = await open('ADMIN');

    expect(el.textContent).toContain('Shadi');
    expect(el.textContent).toContain('Admin');
    expect(logoutButton()).toBeDefined();
    expect(loginLink()).toBeUndefined();
  });

  it('removes account details and actions when the session ends', async () => {
    const { harness, session, el, logoutButton, loginLink } = await open('ADMIN');
    session.end();
    harness.detectChanges();

    expect(el.textContent).not.toContain('Shadi');
    expect(el.textContent).not.toContain('Admin');
    expect(logoutButton()).toBeUndefined();
    expect(loginLink()).toBeDefined();
    expect(el.querySelector('a[href="/register"]')).not.toBeNull();
  });
});
