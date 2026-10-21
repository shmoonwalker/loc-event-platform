import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthSession } from '../../../core/auth-session';
import { LoginPage } from './login-page';

@Component({ template: 'Home' })
class HomeStub {}

async function open() {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([
        { path: 'login', component: LoginPage },
        { path: '', component: HomeStub },
      ]),
    ],
  });
  const harness = await RouterTestingHarness.create('/login');
  const el = harness.routeNativeElement!;

  const type = (id: string, value: string) => {
    const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const fillValid = () => {
    type('email', 'shadi@example.com');
    type('password', 'correct-horse-battery');
  };
  const submit = async () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();
  };

  return { harness, el, http: TestBed.inject(HttpTestingController), fillValid, submit };
}

describe('LoginPage', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('blocks empty input and sends no request', async () => {
    const { el, submit } = await open();
    await submit();

    expect(el.textContent).toContain('Enter your email.');
    expect(el.textContent).toContain('Enter your password.');
  });

  it('logs in, keeps the token in memory only and opens the app', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const { http, harness, fillValid, submit } = await open();
    fillValid();
    await submit();

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'shadi@example.com',
      password: 'correct-horse-battery',
    });
    req.flush({ accessToken: 'jwt-123', tokenType: 'Bearer', expiresIn: 900 });
    await harness.fixture.whenStable();

    expect(TestBed.inject(AuthSession).accessToken()).toBe('jwt-123');
    expect(TestBed.inject(Router).url).toBe('/');
    expect(setItem).not.toHaveBeenCalled();
    setItem.mockRestore();
  });

  it('stays on the form with one generic error for wrong credentials', async () => {
    const { el, http, harness, fillValid, submit } = await open();
    fillValid();
    await submit();

    http
      .expectOne('/api/auth/login')
      .flush(
        { title: 'Invalid credentials', detail: 'Invalid email or password' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await harness.fixture.whenStable();

    expect(el.querySelectorAll('[role="alert"]').length).toBe(1);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'Email or password is incorrect.',
    );
    expect(el.querySelector<HTMLInputElement>('#password')!.value).toBe('');
    expect(TestBed.inject(AuthSession).loggedIn()).toBe(false);
  });

  it('asks to verify the email on 403', async () => {
    const { el, http, harness, fillValid, submit } = await open();
    fillValid();
    await submit();

    http.expectOne('/api/auth/login').flush(null, { status: 403, statusText: 'Forbidden' });
    await harness.fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Verify your email first');
  });

  describe('sending the verification link again after 403', () => {
    const resendButton = (el: HTMLElement) =>
      [...el.querySelectorAll('button')].find(
        (b) => b.textContent?.trim() === 'Send the link again',
      );

    async function unverified() {
      const opened = await open();
      opened.fillValid();
      await opened.submit();
      opened.http
        .expectOne('/api/auth/login')
        .flush(null, { status: 403, statusText: 'Forbidden' });
      await opened.harness.fixture.whenStable();
      return opened;
    }

    it('sends a new link to the email that was typed', async () => {
      const { el, http, harness } = await unverified();
      resendButton(el)!.click();

      const req = http.expectOne('/api/auth/resend-verification');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'shadi@example.com' });
      req.flush(null, { status: 202, statusText: 'Accepted' });
      await harness.fixture.whenStable();

      expect(el.querySelector('[role="status"]')?.textContent).toContain('shadi@example.com');
      expect(resendButton(el)).toBeUndefined();
    });

    it('shows the wait time when resending is rate limited', async () => {
      const { el, http, harness } = await unverified();
      resendButton(el)!.click();

      http.expectOne('/api/auth/resend-verification').flush(null, {
        status: 429,
        statusText: 'Too Many Requests',
        headers: { 'Retry-After': '30' },
      });
      await harness.fixture.whenStable();

      expect(el.querySelector('[role="alert"]')?.textContent).toContain('Try again in 30 seconds');
      expect(resendButton(el)).toBeDefined();
    });

    it('is not offered for wrong credentials', async () => {
      const { el, http, harness, fillValid, submit } = await open();
      fillValid();
      await submit();
      http.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
      await harness.fixture.whenStable();

      expect(resendButton(el)).toBeUndefined();
    });
  });
});
