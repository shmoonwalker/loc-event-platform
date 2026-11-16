import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LoginPage } from '../login/login-page';
import { ForgotPasswordPage } from './forgot-password-page';

async function open(url = '/forgot-password') {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter(
        [
          { path: 'login', component: LoginPage },
          { path: 'forgot-password', component: ForgotPasswordPage },
        ],
        withComponentInputBinding(),
      ),
    ],
  });
  const harness = await RouterTestingHarness.create(url);
  const el = () => harness.routeNativeElement!;

  const typeEmail = (value: string) => {
    const input = el().querySelector<HTMLInputElement>('#email')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const submit = async () => {
    el().querySelector('form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();
  };

  return { harness, el, http: TestBed.inject(HttpTestingController), typeEmail, submit };
}

describe('ForgotPasswordPage', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('blocks an invalid email and sends no request', async () => {
    const { el, typeEmail, submit } = await open();
    await submit();
    expect(el().textContent).toContain('Enter your email.');

    typeEmail('not-an-email');
    await submit();
    expect(el().textContent).toContain('Enter a valid email.');
  });

  it('sends the email and shows the confirmation', async () => {
    const { el, http, harness, typeEmail, submit } = await open();
    typeEmail('shadi@example.com');
    await submit();

    const req = http.expectOne('/api/auth/forgot-password');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'shadi@example.com' });
    req.flush(null, { status: 202, statusText: 'Accepted' });
    await harness.fixture.whenStable();

    expect(el().querySelector('h1')?.textContent).toContain('Check your email');
    expect(el().querySelector('[role="status"]')?.textContent).toContain('shadi@example.com');
    expect(el().querySelector('form')).toBeNull();
    expect(el().querySelector('a[href="/login"]')).toBeTruthy();
  });

  it('shows the same confirmation for every email, because the API answers 202 for all', async () => {
    const confirmations: string[] = [];
    for (const email of ['member@example.com', 'nobody@example.com']) {
      TestBed.resetTestingModule();
      const { el, http, harness, typeEmail, submit } = await open();
      typeEmail(email);
      await submit();
      http
        .expectOne('/api/auth/forgot-password')
        .flush(null, { status: 202, statusText: 'Accepted' });
      await harness.fixture.whenStable();
      confirmations.push(el().textContent!.replace(email, '<email>'));
    }

    expect(confirmations[0]).toBe(confirmations[1]);
  });

  it('shows the API field error under the email', async () => {
    const { el, http, harness, typeEmail, submit } = await open();
    typeEmail('shadi@example.com');
    await submit();

    http
      .expectOne('/api/auth/forgot-password')
      .flush(
        { title: 'Validation failed', errors: { email: 'Please provide a valid email address' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.fixture.whenStable();

    expect(el().querySelector('#email-error')?.textContent).toContain(
      'Please provide a valid email address',
    );
    expect(el().querySelector('form')).toBeTruthy();
  });

  it('shows the wait time when rate limited', async () => {
    const { el, http, harness, typeEmail, submit } = await open();
    typeEmail('shadi@example.com');
    await submit();

    http.expectOne('/api/auth/forgot-password').flush(null, {
      status: 429,
      statusText: 'Too Many Requests',
      headers: { 'Retry-After': '45' },
    });
    await harness.fixture.whenStable();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Try again in 45 seconds');
    expect(el().querySelector('form')).toBeTruthy();
  });

  it('is linked from the login page, with the email already typed there', async () => {
    const { el, harness } = await open('/login');
    const email = el().querySelector<HTMLInputElement>('#email')!;
    email.value = 'shadi@example.com';
    email.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();

    const link = el().querySelector<HTMLAnchorElement>('a[href="/forgot-password"]')!;
    expect(link.textContent).toContain('Forgot password?');
    link.click();
    await harness.fixture.whenStable();

    expect(el().querySelector('h1')?.textContent).toContain('Forgot password');
    expect(el().querySelector<HTMLInputElement>('#email')!.value).toBe('shadi@example.com');
  });
});
