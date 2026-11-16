import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CurrentUser } from '../../core/auth-api';
import { AuthSession } from '../../core/auth-session';
import { ProfileSettings } from './profile-settings';

const USER: CurrentUser = {
  id: 'member-id',
  email: 'shadi@example.com',
  displayName: 'Shadi',
  role: 'USER',
  avatarUrl: '/avatars/sun.svg',
  avatarPreset: 'SUN',
};
const UPLOADED = 'https://images.example.com/avatars/member-id/abc.jpg';

async function open(user: CurrentUser = USER) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([{ path: 'me/settings/profile', component: ProfileSettings }]),
    ],
  });
  const session = TestBed.inject(AuthSession);
  const http = TestBed.inject(HttpTestingController);
  session.start('token').subscribe();
  http.expectOne('/api/auth/me').flush(user);

  const harness = await RouterTestingHarness.create('/me/settings/profile');
  const el = harness.routeNativeElement!;
  const nameInput = () => el.querySelector<HTMLInputElement>('#displayName')!;
  const typeName = (value: string) => {
    nameInput().value = value;
    nameInput().dispatchEvent(new Event('input'));
  };
  const submitName = async () => {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();
  };
  const button = (text: string) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);
  const preset = (name: string) =>
    [...el.querySelectorAll<HTMLButtonElement>('button[aria-pressed]')].find((b) =>
      b.textContent?.includes(name),
    )!;
  const pickFile = async (file: File) => {
    const input = el.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
  };
  const avatar = () => el.querySelector<HTMLImageElement>('img[width="112"]')!;

  return {
    harness,
    el,
    http,
    session,
    nameInput,
    typeName,
    submitName,
    button,
    preset,
    pickFile,
    avatar,
  };
}

const image = (type = 'image/png', size = 1000) =>
  new File([new Uint8Array(size)], 'me.png', { type });

describe('ProfileSettings', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:preview');
    URL.revokeObjectURL = vi.fn();
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('starts with the current name and avatar, with the preset marked', async () => {
    const { nameInput, avatar, preset } = await open();

    expect(nameInput().value).toBe('Shadi');
    expect(avatar().getAttribute('src')).toBe('/avatars/sun.svg');
    expect(preset('sun').getAttribute('aria-pressed')).toBe('true');
    expect(preset('moon').getAttribute('aria-pressed')).toBe('false');
  });

  it('blocks a blank name and sends no request', async () => {
    const { el, typeName, submitName } = await open();
    typeName('   ');
    await submitName();

    expect(el.textContent).toContain('Enter your name.');
  });

  it('saves the name and shows it in the account straight away', async () => {
    const { el, http, harness, session, typeName, submitName } = await open();
    typeName('Shadi B');
    await submitName();

    const req = http.expectOne('/api/me/profile');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ displayName: 'Shadi B' });
    req.flush({ ...USER, displayName: 'Shadi B' });
    await harness.fixture.whenStable();

    expect(session.currentUser()?.displayName).toBe('Shadi B');
    expect(el.textContent).toContain('Name saved.');
  });

  it('shows the API error under the name', async () => {
    const { el, http, harness, session, typeName, submitName } = await open();
    typeName('Shadi B');
    await submitName();

    http
      .expectOne('/api/me/profile')
      .flush(
        { errors: { displayName: 'Display name must not exceed 100 characters' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.fixture.whenStable();

    expect(el.textContent).toContain('Display name must not exceed 100 characters');
    expect(session.currentUser()?.displayName).toBe('Shadi');
  });

  it('picks a preset avatar', async () => {
    const { http, harness, session, preset } = await open();
    preset('moon').click();

    const req = http.expectOne('/api/me/avatar/preset');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ preset: 'MOON' });
    req.flush({ ...USER, avatarPreset: 'MOON', avatarUrl: '/avatars/moon.svg' });
    await harness.fixture.whenStable();

    expect(session.currentUser()?.avatarUrl).toBe('/avatars/moon.svg');
    expect(preset('moon').getAttribute('aria-pressed')).toBe('true');
  });

  it('refuses a file of the wrong type or size without sending it', async () => {
    const { el, pickFile } = await open();

    await pickFile(image('application/pdf'));
    expect(el.textContent).toContain('Choose a JPEG, PNG or WebP image.');

    await pickFile(image('image/jpeg', 5 * 1024 * 1024 + 1));
    expect(el.textContent).toContain('The picture must be 5 MB or smaller.');
  });

  it('previews a picked picture and uploads it on confirm', async () => {
    const { http, harness, session, pickFile, button, avatar } = await open();
    const file = image();
    await pickFile(file);

    expect(avatar().getAttribute('src')).toBe('blob:preview');
    button('Use this picture')!.click();

    const req = http.expectOne('/api/me/avatar');
    expect(req.request.method).toBe('POST');
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush({ ...USER, avatarUrl: UPLOADED });
    await harness.fixture.whenStable();

    expect(session.currentUser()?.avatarUrl).toBe(UPLOADED);
    expect(avatar().getAttribute('src')).toBe(UPLOADED);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
  });

  it('cancels a picked picture without sending it', async () => {
    const { harness, pickFile, button, avatar } = await open();
    await pickFile(image());
    button('Cancel')!.click();
    await harness.fixture.whenStable();

    expect(avatar().getAttribute('src')).toBe('/avatars/sun.svg');
  });

  it('shows why an upload was refused and keeps the preview', async () => {
    const { el, http, harness, pickFile, button, avatar } = await open();
    await pickFile(image());
    button('Use this picture')!.click();

    http
      .expectOne('/api/me/avatar')
      .flush({ title: 'Uploads unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    await harness.fixture.whenStable();

    expect(el.textContent).toContain('Picture uploads are not available right now.');
    expect(avatar().getAttribute('src')).toBe('blob:preview');
  });

  it('shows the backend reason for an unreadable image', async () => {
    const { el, http, harness, pickFile, button } = await open();
    await pickFile(image());
    button('Use this picture')!.click();

    http
      .expectOne('/api/me/avatar')
      .flush(
        { detail: 'The file could not be read as an image' },
        { status: 400, statusText: 'Bad Request' },
      );
    await harness.fixture.whenStable();

    expect(el.textContent).toContain('The file could not be read as an image');
  });

  it('removes an uploaded picture to go back to the preset', async () => {
    const { http, harness, session, button, preset } = await open({ ...USER, avatarUrl: UPLOADED });
    expect(preset('sun').getAttribute('aria-pressed')).toBe('false');
    button('Remove picture')!.click();

    const req = http.expectOne('/api/me/avatar');
    expect(req.request.method).toBe('DELETE');
    req.flush(USER);
    await harness.fixture.whenStable();

    expect(session.currentUser()?.avatarUrl).toBe('/avatars/sun.svg');
    expect(button('Remove picture')).toBeUndefined();
  });

  it('offers no Remove picture without an upload', async () => {
    const { button } = await open();
    expect(button('Remove picture')).toBeUndefined();
  });
});
