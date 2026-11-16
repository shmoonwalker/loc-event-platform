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

/** A decoded 800×600 picture. */
const bitmap = { width: 800, height: 600, close: vi.fn<() => void>() };
let drawImage: ReturnType<typeof vi.fn>;
const lastDraw = () => drawImage.mock.lastCall;

const image = (type = 'image/png', size = 1000) =>
  new File([new Uint8Array(size)], 'me.png', { type });

describe('ProfileSettings', () => {
  beforeEach(() => {
    bitmap.close = vi.fn<() => void>();
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn(async () => bitmap),
    );
    drawImage = vi.fn();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      clearRect: vi.fn(),
      drawImage,
    } as never);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (callback, type) {
      callback(new Blob(['cropped'], { type }));
    });
    // jsdom has no layout, pointer capture or modal dialogs.
    HTMLElement.prototype.setPointerCapture = vi.fn();
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
    };
  });
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

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

  it('shows the centre of a picked picture in the circle', async () => {
    const { el, pickFile, avatar } = await open();
    await pickFile(image());

    expect(avatar()).toBeNull();
    expect(el.querySelector('canvas')?.getAttribute('aria-label')).toBe(
      'Preview of your new picture',
    );
    // 800×600: the largest centred square is 600 wide, starting 100 from the left.
    expect(lastDraw()).toEqual([bitmap, 100, 0, 600, 600, 0, 0, 512, 512]);
  });

  it('zooms in around the centre of the circle', async () => {
    const { harness, el, pickFile } = await open();
    await pickFile(image());
    const zoom = el.querySelector<HTMLInputElement>('input[type="range"]')!;
    zoom.value = '2';
    zoom.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();

    expect(lastDraw()).toEqual([bitmap, 250, 150, 300, 300, 0, 0, 512, 512]);
  });

  it('moves the picture by dragging, never past its edges', async () => {
    const { harness, el, pickFile } = await open();
    await pickFile(image());
    const canvas = el.querySelector('canvas')!;
    const pointer = (type: string, clientX: number) =>
      canvas.dispatchEvent(new MouseEvent(type, { clientX, clientY: 0, bubbles: true }));

    pointer('pointerdown', 100);
    pointer('pointermove', 150);
    await harness.fixture.whenStable();
    // jsdom has no layout, so one CSS pixel counts as 600 / 512 image pixels.
    expect(lastDraw()![1]).toBeCloseTo(100 - (50 * 600) / 512);

    pointer('pointermove', 1000);
    await harness.fixture.whenStable();
    expect(lastDraw()![1]).toBe(0);

    pointer('pointerup', 1000);
    pointer('pointermove', 0);
    await harness.fixture.whenStable();
    expect(lastDraw()![1]).toBe(0);
  });

  it('moves the picture with the arrow keys', async () => {
    const { harness, el, pickFile } = await open();
    await pickFile(image());
    el.querySelector('canvas')!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }),
    );
    await harness.fixture.whenStable();

    expect(lastDraw()![1]).toBe(40);
  });

  it('uploads the cropped square in the format of the picked file', async () => {
    const { http, harness, session, pickFile, button, avatar } = await open();
    await pickFile(image());
    button('Use this picture')!.click();

    expect(HTMLCanvasElement.prototype.toBlob).toHaveBeenCalledWith(
      expect.any(Function),
      'image/png',
      0.92,
    );
    const req = http.expectOne('/api/me/avatar');
    expect(req.request.method).toBe('POST');
    const sent = (req.request.body as FormData).get('file') as File;
    expect(sent.name).toBe('me.png');
    expect(sent.type).toBe('image/png');
    expect(await sent.text()).toBe('cropped');
    req.flush({ ...USER, avatarUrl: UPLOADED });
    await harness.fixture.whenStable();

    expect(session.currentUser()?.avatarUrl).toBe(UPLOADED);
    expect(avatar().getAttribute('src')).toBe(UPLOADED);
    expect(bitmap.close).toHaveBeenCalled();
  });

  it('says so when the picked file cannot be read as an image', async () => {
    const { el, pickFile } = await open();
    vi.mocked(createImageBitmap).mockRejectedValueOnce(new Error('broken'));
    await pickFile(image());

    expect(el.textContent).toContain('The file could not be read as an image.');
    expect(el.querySelector('canvas')).toBeNull();
  });

  it('cancels a picked picture without sending it', async () => {
    const { harness, pickFile, button, avatar } = await open();
    await pickFile(image());
    button('Cancel')!.click();
    await harness.fixture.whenStable();

    expect(avatar().getAttribute('src')).toBe('/avatars/sun.svg');
    expect(bitmap.close).toHaveBeenCalled();
  });

  it('shows why an upload was refused and keeps the crop', async () => {
    const { el, http, harness, pickFile, button } = await open();
    await pickFile(image());
    button('Use this picture')!.click();

    http
      .expectOne('/api/me/avatar')
      .flush({ title: 'Uploads unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    await harness.fixture.whenStable();

    expect(el.textContent).toContain('Picture uploads are not available right now.');
    expect(el.querySelector('canvas')).not.toBeNull();
  });

  it('opens a larger view of the avatar in a dialog', async () => {
    const { el, harness } = await open({ ...USER, avatarUrl: UPLOADED });
    const dialog = el.querySelector('dialog')!;
    expect(dialog.open).toBe(false);

    el.querySelector<HTMLButtonElement>('button[aria-haspopup="dialog"]')!.click();
    await harness.fixture.whenStable();

    expect(dialog.open).toBe(true);
    expect(dialog.getAttribute('aria-labelledby')).toBe('avatar-view-title');
    expect(dialog.querySelector('img')?.getAttribute('src')).toBe(UPLOADED);
    expect(dialog.querySelector('img')?.getAttribute('alt')).toBe('Avatar of Shadi');

    [...dialog.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Close')!.click();
    expect(dialog.open).toBe(false);
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
