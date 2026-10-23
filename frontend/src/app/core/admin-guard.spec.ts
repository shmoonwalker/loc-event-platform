import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CurrentUser } from './auth-api';
import { adminGuard } from './auth-guard';
import { AuthSession } from './auth-session';

@Component({ template: 'Admin actions' })
class AdminStub {}

@Component({ template: 'Public page' })
class PublicStub {}

async function open(role: CurrentUser['role'] | null) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([
        { path: 'admin', component: AdminStub, canActivate: [adminGuard] },
        { path: '', component: PublicStub },
        { path: 'login', component: PublicStub },
      ]),
    ],
  });
  const http = TestBed.inject(HttpTestingController);
  if (role) {
    TestBed.inject(AuthSession).start('token').subscribe();
    http.expectOne('/api/auth/me').flush({
      id: 'member-id',
      email: 'shadi@example.com',
      displayName: 'Shadi',
      role,
    });
  }
  return RouterTestingHarness.create('/admin');
}

describe('adminGuard', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('sends visitors to login', async () => {
    await open(null);
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('keeps members out of admin pages', async () => {
    const harness = await open('USER');
    expect(TestBed.inject(Router).url).toBe('/');
    expect(harness.routeNativeElement?.textContent).not.toContain('Admin actions');
  });

  it('allows admins to open admin pages', async () => {
    const harness = await open('ADMIN');
    expect(TestBed.inject(Router).url).toBe('/admin');
    expect(harness.routeNativeElement?.textContent).toContain('Admin actions');
  });
});
