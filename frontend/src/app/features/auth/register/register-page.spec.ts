import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { RegisterPage } from './register-page';

async function open() {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([{ path: 'register', component: RegisterPage }]),
    ],
  });
  const harness = await RouterTestingHarness.create('/register');
  const el = harness.routeNativeElement!;

  const type = (id: string, value: string) => {
    const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const fillValid = () => {
    type('displayName', 'Shadi');
    type('email', 'shadi@example.com');
    type('password', 'correct-horse-battery');
  };
  const submit = async () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();
  };

  return { harness, el, http: TestBed.inject(HttpTestingController), fillValid, submit };
}

describe('RegisterPage', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('blocks invalid input and sends no request', async () => {
    const { el, submit } = await open();
    await submit();

    expect(el.textContent).toContain('Enter your name.');
    expect(el.textContent).toContain('Enter your email.');
    expect(el.textContent).toContain('Enter a password.');
  });

  it('sends the form and shows the check-your-email screen without the password', async () => {
    const { el, http, harness, fillValid, submit } = await open();
    fillValid();
    await submit();

    const req = http.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      displayName: 'Shadi',
      email: 'shadi@example.com',
      password: 'correct-horse-battery',
    });
    req.flush(null, { status: 202, statusText: 'Accepted' });
    await harness.fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('Check your email');
    expect(el.textContent).toContain('shadi@example.com');
    expect(el.textContent).not.toContain('correct-horse-battery');
  });

  it('shows the API field errors under their fields', async () => {
    const { el, http, harness, fillValid, submit } = await open();
    fillValid();
    await submit();

    http
      .expectOne('/api/auth/register')
      .flush(
        { errors: { passwordWithinByteLimit: 'Password must not exceed 72 bytes' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.fixture.whenStable();

    expect(el.querySelector('#password-error')?.textContent).toContain(
      'Password must not exceed 72 bytes',
    );
  });

  it('asks to try again later on 429', async () => {
    const { el, http, harness, fillValid, submit } = await open();
    fillValid();
    await submit();

    http.expectOne('/api/auth/register').flush(null, {
      status: 429,
      statusText: 'Too Many Requests',
      headers: { 'Retry-After': '42' },
    });
    await harness.fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Try again in 42 seconds');
  });
});
