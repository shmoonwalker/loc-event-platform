import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth-interceptor';
import { AuthSession } from './auth-session';

const NEW_TOKEN = { accessToken: 'new-token', tokenType: 'Bearer', expiresIn: 900 };

function setup(token: string | null) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
    ],
  });
  const session = TestBed.inject(AuthSession);
  if (token) session.start(token);
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  return {
    session,
    navigate,
    client: TestBed.inject(HttpClient),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('authInterceptor', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('sends no Authorization header while signed out', () => {
    const { client, http } = setup(null);
    client.get('/api/events').subscribe();

    expect(http.expectOne('/api/events').request.headers.has('Authorization')).toBe(false);
  });

  it('adds the access token to our own API calls', () => {
    const { client, http } = setup('access-token');
    client.get('/api/auth/me').subscribe();

    expect(http.expectOne('/api/auth/me').request.headers.get('Authorization')).toBe(
      'Bearer access-token',
    );
  });

  it('never sends the token to another site', () => {
    const { client, http } = setup('access-token');
    client.get('https://example.com/api/data').subscribe();

    expect(
      http.expectOne('https://example.com/api/data').request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('sends no token to refresh, so an expired one cannot make the refresh fail', () => {
    const { session, http } = setup('expired-token');
    session.refresh().subscribe();

    const req = http.expectOne('/api/auth/refresh');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(NEW_TOKEN);
    http
      .expectOne('/api/auth/me')
      .flush({ id: 'member-id', email: 'shadi@example.com', displayName: 'Shadi', role: 'USER' });
  });

  it('renews an expired token and retries the call without showing the login page', () => {
    const { client, http, navigate } = setup('expired-token');
    let body: unknown;
    client.get('/api/auth/me').subscribe((b) => (body = b));

    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(NEW_TOKEN);
    http
      .expectOne('/api/auth/me')
      .flush({ id: 'member-id', email: 'shadi@example.com', displayName: 'Shadi', role: 'USER' });
    const retry = http.expectOne('/api/auth/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-token');
    retry.flush({ displayName: 'Shadi' });

    expect(body).toEqual({ displayName: 'Shadi' });
    expect(navigate).not.toHaveBeenCalled();
  });

  it('parallel 401s share one refresh', () => {
    const { client, http } = setup('expired-token');
    client.get('/api/auth/me').subscribe();
    client.get('/api/events').subscribe();

    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/events').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(NEW_TOKEN);
    http
      .expectOne('/api/auth/me')
      .flush({ id: 'member-id', email: 'shadi@example.com', displayName: 'Shadi', role: 'USER' });
    http.expectOne('/api/auth/me').flush({});
    http.expectOne('/api/events').flush({});
  });

  it('a failed refresh ends the session and sends the visitor to login', () => {
    const { client, http, navigate, session } = setup('expired-token');
    let status: number | undefined;
    client.get('/api/auth/me').subscribe({ error: (err) => (status = err.status) });

    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(null, { status: 400, statusText: 'Bad Request' });

    expect(session.loggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
    expect(status).toBe(401);
  });

  it('refreshes only once: a second 401 goes back to the caller', () => {
    const { client, http, navigate } = setup('expired-token');
    let status: number | undefined;
    client.get('/api/auth/me').subscribe({ error: (err) => (status = err.status) });

    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(NEW_TOKEN);
    http
      .expectOne('/api/auth/me')
      .flush({ id: 'member-id', email: 'shadi@example.com', displayName: 'Shadi', role: 'USER' });
    http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not refresh on a 401 from a call without a token, like a wrong password', () => {
    const { client, http } = setup(null);
    let status: number | undefined;
    client.post('/api/auth/login', {}).subscribe({ error: (err) => (status = err.status) });

    http.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
  });

  it('passes other errors through without a refresh', () => {
    const { client, http } = setup('access-token');
    let status: number | undefined;
    client.get('/api/events').subscribe({ error: (err) => (status = err.status) });

    http.expectOne('/api/events').flush(null, { status: 500, statusText: 'Server Error' });

    expect(status).toBe(500);
  });

  it('does not refresh recursively when the account lookup rejects a freshly issued token', () => {
    const { session, http } = setup(null);
    let status: number | undefined;
    session.refresh().subscribe({ error: (err) => (status = err.status) });
    http.expectOne('/api/auth/refresh').flush(NEW_TOKEN);
    const me = http.expectOne('/api/auth/me');
    expect(me.request.headers.get('Authorization')).toBe('Bearer new-token');
    me.flush(null, { status: 401, statusText: 'Unauthorized' });

    http.expectNone('/api/auth/refresh');
    expect(status).toBe(401);
    expect(session.currentUser()).toBeNull();
    expect(session.loggedIn()).toBe(false);
  });
});
