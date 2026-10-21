import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { appConfig } from './app.config';
import { AuthSession } from './core/auth-session';

/** Starts the real app configuration (with a fake backend) and gives back the startup refresh request. */
function boot() {
  TestBed.configureTestingModule({
    providers: [...appConfig.providers, provideHttpClientTesting()],
  });
  const http = TestBed.inject(HttpTestingController);
  return {
    http,
    session: TestBed.inject(AuthSession),
    started: TestBed.inject(ApplicationInitStatus).donePromise,
    refresh: http.expectOne('/api/auth/refresh'),
  };
}

describe('app startup', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('restores the session through the refresh cookie after a reload', async () => {
    const { session, started, refresh } = boot();
    refresh.flush({ accessToken: 'restored', tokenType: 'Bearer', expiresIn: 900 });
    await started;

    expect(session.accessToken()).toBe('restored');
  });

  it('starts signed out, without an error, when there is no valid cookie', async () => {
    const { session, started, refresh } = boot();
    refresh.flush(null, { status: 400, statusText: 'Bad Request' });
    await started;

    expect(session.loggedIn()).toBe(false);
  });

  it('still starts when the backend cannot be reached', async () => {
    const { session, started, refresh } = boot();
    refresh.error(new ProgressEvent('error'));
    await started;

    expect(session.loggedIn()).toBe(false);
  });
});
