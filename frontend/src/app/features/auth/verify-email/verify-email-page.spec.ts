import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthSession } from '../../../core/auth-session';
import { VerifyEmailPage } from './verify-email-page';

async function open(url: string, signedIn = false) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter(
        [{ path: 'verify-email', component: VerifyEmailPage }],
        withComponentInputBinding(),
      ),
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  if (signedIn) {
    TestBed.inject(AuthSession).start('token').subscribe();
    http
      .expectOne('/api/auth/me')
      .flush({ id: 'member-id', email: 'old@example.com', displayName: 'Shadi', role: 'USER' });
  }
  const harness = await RouterTestingHarness.create(url);
  return { harness, el: harness.routeNativeElement!, http };
}

describe('VerifyEmailPage', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('posts the token from the link and shows success with a link to log in', async () => {
    const { harness, el, http } = await open('/verify-email?token=abc');

    const req = http.expectOne('/api/auth/verify-email');
    expect(req.request.body).toEqual({ token: 'abc' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('Email verified');
    expect(el.querySelector('a[href="/login"]')).toBeTruthy();
  });

  it('offers a new link for an invalid or expired token', async () => {
    const { harness, el, http } = await open('/verify-email?token=used');
    http
      .expectOne('/api/auth/verify-email')
      .flush({ title: 'Invalid token' }, { status: 400, statusText: 'Bad Request' });
    await harness.fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toContain('invalid or has expired');

    const email = el.querySelector<HTMLInputElement>('#resend-email')!;
    email.value = 'shadi@example.com';
    email.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/auth/resend-verification');
    expect(req.request.body).toEqual({ email: 'shadi@example.com' });
    req.flush(null, { status: 202, statusText: 'Accepted' });
    await harness.fixture.whenStable();

    expect(el.querySelector('[role="status"]')?.textContent).toContain("we've sent a new link");
  });

  it('confirms a new address for a signed-in member and links back to the account', async () => {
    const { harness, el, http } = await open('/verify-email?token=change', true);

    const req = http.expectOne('/api/auth/verify-email');
    expect(req.request.body).toEqual({ token: 'change' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(el.textContent).toContain('Your new email address is confirmed.');
    expect(el.querySelector('a[href="/account"]')).toBeTruthy();
    expect(el.querySelector('a[href="/login"]')).toBeNull();
  });

  it('sends a signed-in member to the account settings for a new link', async () => {
    const { harness, el, http } = await open('/verify-email?token=used', true);
    http
      .expectOne('/api/auth/verify-email')
      .flush({ title: 'Invalid token' }, { status: 400, statusText: 'Bad Request' });
    await harness.fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('invalid or has expired');
    expect(el.querySelector('a[href="/account"]')).toBeTruthy();
    expect(el.querySelector('#resend-email')).toBeNull();
  });
});
