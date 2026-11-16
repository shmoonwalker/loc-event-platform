import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthSession } from '../../core/auth-session';
import { Shell } from '../../layout/shell';
import { FeedbackPage } from './feedback-page';

async function render(signedIn = false) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  if (signedIn) TestBed.inject(AuthSession).start('token');
  const fixture = TestBed.createComponent(FeedbackPage);
  await fixture.whenStable();

  const el = fixture.nativeElement as HTMLElement;
  const http = TestBed.inject(HttpTestingController);
  const type = (selector: string, value: string) => {
    const field = el.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
  };
  const submit = () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };
  return { fixture, el, http, type, submit, settle: () => fixture.whenStable() };
}

describe('FeedbackPage', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lets a visitor send feedback with a contact email, then thanks them', async () => {
    const { el, http, type, submit, settle } = await render();
    type('#feedback-message', '  The map is hard to use.  ');
    type('#feedback-email', ' anouk@example.com ');
    submit();

    const req = http.expectOne('/api/feedback');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      message: 'The map is hard to use.',
      contactEmail: 'anouk@example.com',
    });
    req.flush({}, { status: 201, statusText: 'Created' });
    await settle();

    expect(el.querySelector('[role="status"]')?.textContent).toContain('Thanks for your feedback.');
    expect(el.querySelector('form')).toBeNull();
  });

  it('sends no email when the visitor leaves it empty', async () => {
    const { http, type, submit } = await render();
    type('#feedback-message', 'Nice site');
    submit();
    expect(http.expectOne('/api/feedback').request.body).toEqual({
      message: 'Nice site',
      contactEmail: null,
    });
  });

  it('does not ask a member for an email', async () => {
    const { el, http, type, submit } = await render(true);
    expect(el.querySelector('#feedback-email')).toBeNull();
    type('#feedback-message', 'Nice site');
    submit();
    expect(http.expectOne('/api/feedback').request.body.contactEmail).toBeNull();
  });

  it('checks the message and email before sending anything', async () => {
    const { el, http, type, submit } = await render();
    type('#feedback-email', 'not-an-email');
    submit();

    http.expectNone('/api/feedback');
    expect(el.querySelector('#feedback-message-error')?.textContent).toContain(
      'Write a message first.',
    );
    expect(el.querySelector('#feedback-email-error')?.textContent).toContain(
      'Enter a valid email.',
    );
  });

  it('shows the rate limit with its wait time', async () => {
    const { el, http, type, submit, settle } = await render();
    type('#feedback-message', 'Nice site');
    submit();
    http
      .expectOne('/api/feedback')
      .flush(
        { title: 'Too many requests' },
        { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '1200' } },
      );
    await settle();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      "You've sent a lot of feedback. Try again in 1200 seconds.",
    );
  });

  it('puts the API field errors under their fields', async () => {
    const { el, http, type, submit, settle } = await render();
    type('#feedback-message', 'Nice site');
    type('#feedback-email', 'a@b.c');
    submit();
    http
      .expectOne('/api/feedback')
      .flush(
        { errors: { contactEmail: 'Please provide a valid email address' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();

    expect(el.querySelector('#feedback-email-error')?.textContent).toContain(
      'Please provide a valid email address',
    );
  });

  it('starts a new message from the thank-you', async () => {
    const { el, http, type, submit, settle } = await render();
    type('#feedback-message', 'Nice site');
    submit();
    http.expectOne('/api/feedback').flush({}, { status: 201, statusText: 'Created' });
    await settle();

    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Send more'))!.click();
    await settle();
    expect(el.querySelector<HTMLTextAreaElement>('#feedback-message')?.value).toBe('');
  });
});

describe('Shell footer', () => {
  it('links to the feedback page', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/cities').flush([]);
    await fixture.whenStable();

    const link = (fixture.nativeElement as HTMLElement).querySelector('footer a');
    expect(link?.textContent?.trim()).toBe('Send feedback');
    expect(link?.getAttribute('href')).toBe('/feedback');
  });
});
