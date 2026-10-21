import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthSession } from '../core/auth-session';
import { SiteHeader } from './site-header';

@Component({ template: 'Home' })
class HomeStub {}

/** Shows the header on a page that is not home, so the test can see logout going back to "/". */
async function open(token: string | null) {
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
  if (token) session.start(token);
  const harness = await RouterTestingHarness.create('/events/1');
  const http = TestBed.inject(HttpTestingController);
  http.expectOne((req) => req.url === '/api/cities').flush([]);
  harness.detectChanges();

  const el = harness.routeNativeElement!;
  const logoutButton = () =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Log out');
  const loginLink = () =>
    [...el.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Log in');

  return { harness, http, session, logoutButton, loginLink };
}

describe('SiteHeader', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows Log in while signed out', async () => {
    const { logoutButton, loginLink } = await open(null);

    expect(loginLink()).toBeDefined();
    expect(logoutButton()).toBeUndefined();
  });

  it('shows Log out instead of Log in while signed in', async () => {
    const { logoutButton, loginLink } = await open('token');

    expect(logoutButton()).toBeDefined();
    expect(loginLink()).toBeUndefined();
  });

  it('logout ends the session on the server and in the UI, then opens the home page', async () => {
    const { harness, http, session, logoutButton } = await open('token');
    logoutButton()!.click();

    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(session.loggedIn()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('still logs out in the UI when the server cannot be reached', async () => {
    const { harness, http, session, logoutButton } = await open('token');
    logoutButton()!.click();

    http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));
    await harness.fixture.whenStable();

    expect(session.loggedIn()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/');
  });
});
