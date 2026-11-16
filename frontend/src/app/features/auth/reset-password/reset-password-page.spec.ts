import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthSession } from '../../../core/auth-session';
import { ResetPasswordPage } from './reset-password-page';

async function open(url: string, signedIn = false) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter(
        [{ path: 'reset-password', component: ResetPasswordPage }],
        withComponentInputBinding(),
      ),
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  if (signedIn) {
    TestBed.inject(AuthSession).start('token').subscribe();
    http
      .expectOne('/api/auth/me')
      .flush({ id: 'member-id', email: 'shadi@example.com', displayName: 'Shadi', role: 'USER' });
  }
  const harness = await RouterTestingHarness.create(url);
  const el = harness.routeNativeElement!;

  const typePassword = (value: string) => {
    const input = el.querySelector<HTMLInputElement>('#newPassword')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const submit = async () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();
  };

  return { harness, el, http, typePassword, submit };
}

describe('ResetPasswordPage', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('sets the new password with the token from the link and leads to login', async () => {
    const { el, http, harness, typePassword, submit } = await open('/reset-password?token=abc');
    typePassword('correct-horse-battery');
    await submit();

    const req = http.expectOne('/api/auth/reset-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'abc', newPassword: 'correct-horse-battery' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('Password changed');
    expect(el.querySelector('a[href="/login"]')).toBeTruthy();
    expect(el.querySelector('form')).toBeNull();
  });

  it('ends a local session after the reset, since the server signed out every session', async () => {
    const { http, harness, typePassword, submit } = await open('/reset-password?token=abc', true);
    expect(TestBed.inject(AuthSession).loggedIn()).toBe(true);
    typePassword('correct-horse-battery');
    await submit();

    http
      .expectOne('/api/auth/reset-password')
      .flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(TestBed.inject(AuthSession).loggedIn()).toBe(false);
    expect(TestBed.inject(AuthSession).currentUser()).toBeNull();
  });

  it('checks the same password rules as sign-up before sending', async () => {
    const { el, typePassword, submit } = await open('/reset-password?token=abc');
    await submit();
    expect(el.querySelector('#newPassword-error')?.textContent).toContain('Enter a new password.');

    typePassword('short');
    await submit();
    expect(el.querySelector('#newPassword-error')?.textContent).toContain(
      'Use at least 10 characters.',
    );

    typePassword('🔒'.repeat(19));
    await submit();
    expect(el.querySelector('#newPassword-error')?.textContent).toContain(
      'This password is too long.',
    );
  });

  it('shows and hides the password', async () => {
    const { el, harness } = await open('/reset-password?token=abc');
    const input = el.querySelector<HTMLInputElement>('#newPassword')!;
    const toggle = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Show'))!;
    expect(input.type).toBe('password');

    toggle.click();
    await harness.fixture.whenStable();
    expect(input.type).toBe('text');
    expect(toggle.textContent).toContain('Hide');
  });

  it('shows the API field errors under the password', async () => {
    const { el, http, harness, typePassword, submit } = await open('/reset-password?token=abc');
    typePassword('correct-horse-battery');
    await submit();

    http
      .expectOne('/api/auth/reset-password')
      .flush(
        { title: 'Validation failed', errors: { newPasswordWithinByteLimit: 'Too many bytes' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.fixture.whenStable();

    expect(el.querySelector('#newPassword-error')?.textContent).toContain('Too many bytes');
    expect(el.querySelector('form')).toBeTruthy();
  });

  it('cannot submit with an invalid, expired or used token and offers a new link', async () => {
    const { el, http, harness, typePassword, submit } = await open('/reset-password?token=used');
    typePassword('correct-horse-battery');
    await submit();

    http
      .expectOne('/api/auth/reset-password')
      .flush(
        { title: 'Invalid token', detail: 'This link is invalid or has expired' },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('invalid or has expired');
    expect(el.querySelector('form')).toBeNull();
    expect(el.querySelector('a[href="/forgot-password"]')).toBeTruthy();
  });

  it('shows no form without a token', async () => {
    const { el } = await open('/reset-password');

    expect(el.querySelector('h1')?.textContent).toContain('invalid or has expired');
    expect(el.querySelector('#newPassword')).toBeNull();
    expect(el.querySelector('a[href="/forgot-password"]')).toBeTruthy();
  });

  it('keeps the form with a message when the server cannot be reached', async () => {
    const { el, http, harness, typePassword, submit } = await open('/reset-password?token=abc');
    typePassword('correct-horse-battery');
    await submit();

    http.expectOne('/api/auth/reset-password').error(new ProgressEvent('error'));
    await harness.fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain("couldn't change");
    expect(el.querySelector('form')).toBeTruthy();
  });
});
