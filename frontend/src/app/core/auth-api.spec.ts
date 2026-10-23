import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthApi, CurrentUser } from './auth-api';

describe('AuthApi', () => {
  it('loads the current account from the backend', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const user: CurrentUser = {
      id: 'member-id',
      email: 'shadi@example.com',
      displayName: 'Shadi',
      role: 'USER',
    };
    let current: CurrentUser | undefined;
    TestBed.inject(AuthApi).me().subscribe((value) => (current = value));

    const req = http.expectOne('/api/auth/me');
    expect(req.request.method).toBe('GET');
    req.flush(user);

    expect(current).toEqual(user);
    http.verify();
  });
});
