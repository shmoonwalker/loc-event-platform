import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { authInterceptor } from '../../core/auth-interceptor';
import { AuthSession } from '../../core/auth-session';
import { AccountPage } from './account-page';

@Component({ template: 'Home' })
class HomeStub {}

const WRONG_PASSWORD = { title: 'Invalid credentials', detail: 'Invalid email or password' };

/** Signed in, with the real interceptor, so a wrong password shows it does not start a refresh. */
async function open() {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
      provideRouter([
        { path: 'account', component: AccountPage },
        { path: '', component: HomeStub },
      ]),
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  const session = TestBed.inject(AuthSession);
  session.start('token').subscribe();
  http
    .expectOne('/api/auth/me')
    .flush({ id: 'member-id', email: 'shadi@example.com', displayName: 'Shadi', role: 'USER' });
  const harness = await RouterTestingHarness.create('/account');
  const el = harness.routeNativeElement!;

  const section = (title: string) =>
    [...el.querySelectorAll('section')].find(
      (s) => s.querySelector('h2')?.textContent?.trim() === title,
    )!;
  const type = (selector: string, value: string) => {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const submit = async (title: string) => {
    section(title).querySelector('form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();
  };
  const settle = () => harness.fixture.whenStable();
  return { harness, http, session, el, section, type, submit, settle };
}

describe('AccountPage', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows and hides each password field on its own', async () => {
    const { el, settle } = await open();
    const ids = ['#password-current', '#password-new', '#email-password', '#delete-password'];
    const typeOf = (id: string) => el.querySelector<HTMLInputElement>(id)!.type;
    expect(ids.map(typeOf)).toEqual(['password', 'password', 'password', 'password']);

    const toggle = (id: string) =>
      el.querySelector(id)!.parentElement!.querySelector<HTMLButtonElement>('button')!;
    toggle('#password-new').click();
    await settle();
    expect(ids.map(typeOf)).toEqual(['password', 'text', 'password', 'password']);
    expect(toggle('#password-new').textContent).toContain('Hide');

    toggle('#password-new').click();
    await settle();
    expect(typeOf('#password-new')).toBe('password');
  });

  describe('password', () => {
    it('checks the fields with the sign-up rules before sending anything', async () => {
      const { el, type, submit } = await open();
      type('#password-new', 'short');
      await submit('Password');

      expect(el.querySelector('#password-current-error')?.textContent).toContain(
        'Enter your current password.',
      );
      expect(el.querySelector('#password-new-error')?.textContent).toContain(
        'Use at least 10 characters.',
      );
      expect(el.querySelector('#password-new')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('changes the password, empties the form and says other devices are signed out', async () => {
      const { http, el, section, type, submit, settle } = await open();
      expect(section('Password').textContent).toContain('signs you out on your other devices');
      type('#password-current', 'old-password-123');
      type('#password-new', 'new-password-456');
      await submit('Password');

      const req = http.expectOne('/api/me/password');
      expect(req.request.body).toEqual({
        currentPassword: 'old-password-123',
        newPassword: 'new-password-456',
      });
      req.flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(section('Password').querySelector('[role="status"]')?.textContent).toContain(
        'other devices have been signed out',
      );
      expect(el.querySelector<HTMLInputElement>('#password-current')!.value).toBe('');
      expect(el.querySelector<HTMLInputElement>('#password-new')!.value).toBe('');
    });

    it('shows a wrong current password under its field without refreshing the session', async () => {
      const { http, el, type, submit, settle } = await open();
      type('#password-current', 'wrong-password');
      type('#password-new', 'new-password-456');
      await submit('Password');

      http
        .expectOne('/api/me/password')
        .flush(WRONG_PASSWORD, { status: 401, statusText: 'Unauthorized' });
      await settle();

      http.expectNone('/api/auth/refresh');
      expect(el.querySelector('#password-current-error')?.textContent).toContain(
        'Your current password is incorrect.',
      );
    });

    it('puts the API field errors under their fields', async () => {
      const { http, el, type, submit, settle } = await open();
      type('#password-current', 'old-password-123');
      type('#password-new', 'new-password-456');
      await submit('Password');

      http
        .expectOne('/api/me/password')
        .flush(
          { title: 'Validation failed', errors: { newPasswordWithinByteLimit: 'Too long' } },
          { status: 400, statusText: 'Bad Request' },
        );
      await settle();

      expect(el.querySelector('#password-new-error')?.textContent).toContain('Too long');
    });

    it('reports the rate limit with its wait time', async () => {
      const { http, section, type, submit, settle } = await open();
      type('#password-current', 'old-password-123');
      type('#password-new', 'new-password-456');
      await submit('Password');

      http
        .expectOne('/api/me/password')
        .flush(
          { title: 'Too many requests' },
          { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '120' } },
        );
      await settle();

      expect(section('Password').querySelector('[role="alert"]')?.textContent).toContain(
        'Try again in 120 seconds.',
      );
    });
  });

  describe('email', () => {
    it('checks the new address before sending anything', async () => {
      const { http, el, type, submit } = await open();
      type('#email-new', 'not-an-email');
      await submit('Email');

      http.expectNone('/api/me/email');
      expect(el.querySelector('#email-new-error')?.textContent).toContain('Enter a valid email.');
      expect(el.querySelector('#email-password-error')?.textContent).toContain(
        'Enter your current password.',
      );
    });

    it('sends the new address with the password, then asks to check the new inbox', async () => {
      const { http, section, type, submit, settle } = await open();
      type('#email-new', 'new@example.com');
      type('#email-password', 'old-password-123');
      await submit('Email');

      const req = http.expectOne('/api/me/email');
      expect(req.request.body).toEqual({
        currentPassword: 'old-password-123',
        newEmail: 'new@example.com',
      });
      req.flush(null, { status: 202, statusText: 'Accepted' });
      await settle();

      const status = section('Email').querySelector('[role="status"]')?.textContent;
      expect(status).toContain('Check your new inbox');
      expect(status).toContain('new@example.com');
    });

    it('shows a wrong current password under its field', async () => {
      const { http, el, type, submit, settle } = await open();
      type('#email-new', 'new@example.com');
      type('#email-password', 'wrong-password');
      await submit('Email');

      http
        .expectOne('/api/me/email')
        .flush(WRONG_PASSWORD, { status: 401, statusText: 'Unauthorized' });
      await settle();

      expect(el.querySelector('#email-password-error')?.textContent).toContain(
        'Your current password is incorrect.',
      );
    });

    it('shows a failed request above the button', async () => {
      const { http, section, type, submit, settle } = await open();
      type('#email-new', 'new@example.com');
      type('#email-password', 'old-password-123');
      await submit('Email');

      http.expectOne('/api/me/email').error(new ProgressEvent('error'));
      await settle();

      expect(section('Email').querySelector('[role="alert"]')?.textContent).toContain(
        "We couldn't save this change.",
      );
    });
  });

  describe('deletion', () => {
    const button = (el: HTMLElement, label: string) =>
      [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === label);

    it('explains what is removed and needs the password before asking to confirm', async () => {
      const { http, el, section, submit } = await open();
      expect(section('Delete account').textContent).toContain('saved events');
      expect(section('Delete account').textContent).toContain('Deleted user');
      await submit('Delete account');

      http.expectNone('/api/me');
      expect(el.querySelector('#delete-password-error')?.textContent).toContain(
        'Enter your current password.',
      );
      expect(button(el, 'Yes, delete my account')).toBeUndefined();
    });

    it('asks to confirm, deletes, signs the member out and opens the home page', async () => {
      const { http, el, session, type, submit, settle } = await open();
      type('#delete-password', 'old-password-123');
      await submit('Delete account');
      http.expectNone('/api/me');
      expect(button(el, 'Yes, delete my account')).toBeDefined();

      await submit('Delete account');
      const req = http.expectOne('/api/me');
      expect(req.request.method).toBe('DELETE');
      expect(req.request.body).toEqual({ currentPassword: 'old-password-123' });
      req.flush(null, { status: 204, statusText: 'No Content' });
      await settle();

      expect(session.loggedIn()).toBe(false);
      expect(session.currentUser()).toBeNull();
      expect(TestBed.inject(Router).url).toBe('/');
    });

    it('cancelling the confirmation sends nothing', async () => {
      const { harness, http, el, type, submit } = await open();
      type('#delete-password', 'old-password-123');
      await submit('Delete account');
      button(el, 'Cancel')!.click();
      harness.detectChanges();

      http.expectNone('/api/me');
      expect(button(el, 'Yes, delete my account')).toBeUndefined();
      expect(button(el, 'Delete account')).toBeDefined();
    });

    it('keeps the member signed in when the password is wrong', async () => {
      const { http, el, session, type, submit, settle } = await open();
      type('#delete-password', 'wrong-password');
      await submit('Delete account');
      await submit('Delete account');

      http.expectOne('/api/me').flush(WRONG_PASSWORD, { status: 401, statusText: 'Unauthorized' });
      await settle();

      http.expectNone('/api/auth/refresh');
      expect(session.loggedIn()).toBe(true);
      expect(el.querySelector('#delete-password-error')?.textContent).toContain(
        'Your current password is incorrect.',
      );
      expect(TestBed.inject(Router).url).toBe('/account');
    });
  });
});
