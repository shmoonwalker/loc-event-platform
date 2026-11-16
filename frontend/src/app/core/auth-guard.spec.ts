import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { authGuard, needsAccount } from './auth-guard';
import { AuthSession } from './auth-session';

@Component({ template: 'Members only' })
class MemberPageStub {}

@Component({ template: 'Log in' })
class LoginStub {}

async function open(url: string, token: string | null) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'account', component: MemberPageStub, canActivate: [authGuard] },
        { path: 'login', component: LoginStub },
      ]),
    ],
  });
  if (token) TestBed.inject(AuthSession).start(token);
  return RouterTestingHarness.create(url);
}

describe('authGuard', () => {
  it('sends a signed-out visitor to the login page', async () => {
    const harness = await open('/account', null);

    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Faccount');
    expect(harness.routeNativeElement?.textContent).toContain('Log in');
  });

  it('opens the page for a signed-in member', async () => {
    const harness = await open('/account', 'token');

    expect(TestBed.inject(Router).url).toBe('/account');
    expect(harness.routeNativeElement?.textContent).toContain('Members only');
  });
});

describe('needsAccount', () => {
  async function pageAt(url: string) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'events', component: LoginStub },
          {
            path: 'settings',
            canActivate: [authGuard],
            children: [{ path: 'profile', component: MemberPageStub }],
          },
        ]),
      ],
    });
    TestBed.inject(AuthSession).start('token');
    await RouterTestingHarness.create(url);
    return needsAccount(TestBed.inject(Router).routerState.snapshot.root);
  }

  it('is false on a public page', async () => {
    expect(await pageAt('/events')).toBe(false);
  });

  it('is true on a page inside a guarded route', async () => {
    expect(await pageAt('/settings/profile')).toBe(true);
  });
});
