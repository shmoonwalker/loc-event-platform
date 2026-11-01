import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthSession } from '../../core/auth-session';
import { SaveButton } from './save-button';

function render(saved: boolean, signedIn = true) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  if (signedIn) TestBed.inject(AuthSession).start('token');
  const fixture = TestBed.createComponent(SaveButton);
  fixture.componentRef.setInput('eventId', 'e1');
  fixture.componentRef.setInput('initiallySaved', saved);
  fixture.detectChanges();
  const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;
  const click = () => {
    button.click();
    fixture.detectChanges();
  };
  return { fixture, button, click, http: TestBed.inject(HttpTestingController) };
}

describe('SaveButton', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('saves at once, before the server answers', () => {
    const { button, click, http } = render(false);
    expect(button.getAttribute('aria-pressed')).toBe('false');

    click();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    const req = http.expectOne('/api/me/saved-events/e1');
    expect(req.request.method).toBe('PUT');
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(button.getAttribute('aria-pressed')).toBe('true');
  });

  it('unsaves a saved event', () => {
    const { button, click, http } = render(true);
    click();
    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(http.expectOne('/api/me/saved-events/e1').request.method).toBe('DELETE');
  });

  it('rolls back when the call fails', () => {
    const { button, click, http, fixture } = render(false);
    click();
    http
      .expectOne('/api/me/saved-events/e1')
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('false');
  });

  it('ignores clicks while a call is running, so a double click sends one request', () => {
    const { button, click, http } = render(false);
    click();
    click();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    http.expectOne('/api/me/saved-events/e1');
  });

  it('sends a signed-out visitor to login with the way back, without calling the API', () => {
    const { click } = render(false, false);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    click();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
  });

  it('follows a new value from the server', () => {
    const { button, fixture } = render(false);
    fixture.componentRef.setInput('initiallySaved', true);
    fixture.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('true');
  });
});
