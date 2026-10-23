import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthSession } from './auth-session';

const USER = { id: 'member-id', email: 'shadi@example.com', displayName: 'Shadi', role: 'USER' };
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
    http.expectOne('/api/auth/me').flush(USER);

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
    expect(session.currentUser()).toBeNull();
    expect(session.isAdmin()).toBe(false);
  });

  it('retries once after 409, when another tab has just rotated the cookie', () => {
    const { session, http } = setup();
    session.refresh().subscribe();

    http.expectOne('/api/auth/refresh').flush(null, { status: 409, statusText: 'Conflict' });
    http.expectOne('/api/auth/refresh').flush(TOKEN);
    http.expectOne('/api/auth/me').flush(USER);

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
    expect(session.currentUser()).toBeNull();
    expect(session.isAdmin()).toBe(false);
  });

  it('parallel callers share one refresh request', () => {
    const { session, http } = setup();
    const tokens: string[] = [];
    session.refresh().subscribe((t) => tokens.push(t));
    session.refresh().subscribe((t) => tokens.push(t));

    http.expectOne('/api/auth/refresh').flush(TOKEN);
    http.expectOne('/api/auth/me').flush(USER);

    expect(tokens).toEqual(['new-token', 'new-token']);
  });

  it('sends a new request once the previous refresh has finished', () => {
    const { session, http } = setup();
    session.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush(TOKEN);
    http.expectOne('/api/auth/me').flush(USER);

    session.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush(TOKEN);
    http.expectOne('/api/auth/me').flush(USER);
  });

  it('logout tells the server and ends the session', () => {
    const { session, http } = setup();
    session.start('token').subscribe();
    http.expectOne('/api/auth/me').flush({ ...USER, role: 'ADMIN' });
    session.logout().subscribe();

    const req = http.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(session.loggedIn()).toBe(false);
    expect(session.currentUser()).toBeNull();
    expect(session.isAdmin()).toBe(false);
  });

  it('logout ends the session in the UI even when the server cannot be reached', () => {
    const { session, http } = setup();
    session.start('token').subscribe();
    http.expectOne('/api/auth/me').flush({ ...USER, role: 'ADMIN' });
    session.logout().subscribe({ error: () => undefined });

    http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));

    expect(session.loggedIn()).toBe(false);
    expect(session.currentUser()).toBeNull();
    expect(session.isAdmin()).toBe(false);
  });

  it('loads the account after sign-in and exposes its role', () => {
    const { session, http } = setup();
    session.start('token').subscribe();
    expect(session.currentUser()).toBeNull();
    expect(session.isAdmin()).toBe(false);

    http.expectOne('/api/auth/me').flush({ ...USER, role: 'ADMIN' });
    expect(session.currentUser()?.displayName).toBe('Shadi');
    expect(session.isAdmin()).toBe(true);

    session.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush(TOKEN);
    http.expectOne('/api/auth/me').flush(USER);
    expect(session.isAdmin()).toBe(false);
  });

  it('clears the old account when a different sign-in starts', () => {
    const { session, http } = setup();
    session.start('admin-token').subscribe();
    http.expectOne('/api/auth/me').flush({ ...USER, role: 'ADMIN' });

    session.start('member-token').subscribe();
    expect(session.currentUser()).toBeNull();
    expect(session.isAdmin()).toBe(false);
    http.expectOne('/api/auth/me').flush(USER);
  });

  it('a failed account lookup clears the session and passes the error on', () => {
    const { session, http } = setup();
    let status: number | undefined;
    session.refresh().subscribe({ error: (err) => (status = err.status) });
    http.expectOne('/api/auth/refresh').flush(TOKEN);
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(session.currentUser()).toBeNull();
    expect(session.loggedIn()).toBe(false);
    expect(session.isAdmin()).toBe(false);
  });

  it('ending the session cancels a pending account lookup', () => {
    const { session, http } = setup();
    session.start('token').subscribe();
    const me = http.expectOne('/api/auth/me');
    session.end();

    expect(me.cancelled).toBe(true);
    expect(session.currentUser()).toBeNull();
    expect(session.loggedIn()).toBe(false);
    expect(session.isAdmin()).toBe(false);
  });

  it('ending the session cancels a pending refresh', () => {
    const { session, http } = setup();
    session.refresh().subscribe();
    const refresh = http.expectOne('/api/auth/refresh');
    session.end();

    expect(refresh.cancelled).toBe(true);
    expect(session.currentUser()).toBeNull();
    expect(session.loggedIn()).toBe(false);
    expect(session.isAdmin()).toBe(false);
  });
});
