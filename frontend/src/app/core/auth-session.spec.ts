import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthSession } from './auth-session';

const TOKEN = { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 900 };

function setup() {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return { session: TestBed.inject(AuthSession), http: TestBed.inject(HttpTestingController) };
}

describe('AuthSession', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('refresh starts the session with the new token', () => {
    const { session, http } = setup();
    let token: string | undefined;
    session.refresh().subscribe((t) => (token = t));

    const req = http.expectOne('/api/auth/refresh');
    expect(req.request.method).toBe('POST');
    req.flush(TOKEN);

    expect(token).toBe('new-token');
    expect(session.accessToken()).toBe('new-token');
    expect(session.loggedIn()).toBe(true);
  });

  it('a refused refresh (400) ends the session and passes the error on', () => {
    const { session, http } = setup();
    session.start('old-token');
    let status: number | undefined;
    session.refresh().subscribe({ error: (err) => (status = err.status) });

    http.expectOne('/api/auth/refresh').flush(null, { status: 400, statusText: 'Bad Request' });

    expect(status).toBe(400);
    expect(session.loggedIn()).toBe(false);
  });

  it('retries once after 409, when another tab has just rotated the cookie', () => {
    const { session, http } = setup();
    session.refresh().subscribe();

    http.expectOne('/api/auth/refresh').flush(null, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/auth/refresh').flush(TOKEN);

    expect(session.accessToken()).toBe('new-token');
  });

  it('gives up after a second 409', () => {
    const { session, http } = setup();
    let failed = false;
    session.refresh().subscribe({ error: () => (failed = true) });

    http.expectOne('/api/auth/refresh').flush(null, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/auth/refresh').flush(null, { status: 409, statusText: 'Conflict' });

    expect(failed).toBe(true);
    expect(session.loggedIn()).toBe(false);
  });

  it('parallel callers share one refresh request', () => {
    const { session, http } = setup();
    const tokens: string[] = [];
    session.refresh().subscribe((t) => tokens.push(t));
    session.refresh().subscribe((t) => tokens.push(t));

    http.expectOne('/api/auth/refresh').flush(TOKEN);

    expect(tokens).toEqual(['new-token', 'new-token']);
  });

  it('sends a new request once the previous refresh has finished', () => {
    const { session, http } = setup();
    session.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush(TOKEN);

    session.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush(TOKEN);
  });

  it('logout tells the server and ends the session', () => {
    const { session, http } = setup();
    session.start('token');
    session.logout().subscribe();

    const req = http.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(session.loggedIn()).toBe(false);
  });

  it('logout ends the session in the UI even when the server cannot be reached', () => {
    const { session, http } = setup();
    session.start('token');
    session.logout().subscribe({ error: () => undefined });

    http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));

    expect(session.loggedIn()).toBe(false);
  });
});
