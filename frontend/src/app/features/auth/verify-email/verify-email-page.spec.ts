import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { VerifyEmailPage } from './verify-email-page';

async function open(url: string) {
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
  const harness = await RouterTestingHarness.create(url);
  return { harness, el: harness.routeNativeElement!, http: TestBed.inject(HttpTestingController) };
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
});
