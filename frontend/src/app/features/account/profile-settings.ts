import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AVATAR_PRESETS, AvatarPreset, CurrentUser } from '../../core/auth-api';
import { AuthSession } from '../../core/auth-session';
import { DISPLAY_NAME_MESSAGES, displayNameValidators } from '../../core/display-name';
import { ProfileApi } from '../../core/profile-api';
import { CROP_SIZE, Crop, MAX_ZOOM, centeredCrop, cropSquare, moveCrop } from './avatar-crop';

/** The backend's upload limits, checked here first so a wrong file never leaves the browser. */
const UPLOAD_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const UPLOAD_MAX_BYTES = 5 * 1024 * 1024;

/** The image of a preset avatar; the backend uses the same path. */
export const presetUrl = (preset: AvatarPreset) => `/avatars/${preset.toLowerCase()}.svg`;

/**
 * The "Profile" section of the account settings: the display name and the avatar (a preset, or an uploaded picture).
 * Every change hands the returned account to AuthSession, so the header updates at once.
 * A picked picture is cropped in the browser (drag and zoom inside the circle) and only the square is uploaded.
 */
@Component({
  selector: 'app-profile-settings',
  imports: [ReactiveFormsModule],
  templateUrl: './profile-settings.html',
})
export class ProfileSettings {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly api = inject(ProfileApi);
  protected readonly session = inject(AuthSession);

  protected readonly presets = AVATAR_PRESETS;
  protected readonly presetUrl = presetUrl;

  // authGuard and the app start-up load the account before this page opens.
  protected readonly form = this.fb.group({
    displayName: [this.session.currentUser()?.displayName ?? '', displayNameValidators],
  });
  protected readonly nameStatus = signal<'idle' | 'saving' | 'saved'>('idle');
  protected readonly nameError = signal<string | null>(null);

  /** True when the account shows an uploaded picture instead of its preset. */
  protected readonly hasUpload = computed(() => {
    const user = this.session.currentUser();
    return !!user && user.avatarUrl !== presetUrl(user.avatarPreset);
  });

  /** The picked file and its decoded image, until it is uploaded or cancelled. */
  protected readonly picked = signal<{ file: File; image: ImageBitmap } | null>(null);
  protected readonly crop = signal<Crop>({ zoom: 1, centerX: 0, centerY: 0 });
  protected readonly avatarBusy = signal(false);
  protected readonly avatarError = signal<string | null>(null);

  protected readonly cropSize = CROP_SIZE;
  protected readonly maxZoom = MAX_ZOOM;
  private readonly canvas = viewChild<ElementRef<HTMLCanvasElement>>('cropCanvas');
  /** Pointer position of a drag in progress, in CSS pixels. */
  private dragFrom: { x: number; y: number } | null = null;
  /** Counts picks, so a slow decode of an older pick is dropped. */
  private pickCount = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.clearPicked());
    // The canvas is both the round preview and the picture that is uploaded.
    afterRenderEffect(() => {
      const picked = this.picked();
      const context = this.canvas()?.nativeElement.getContext('2d');
      if (!picked || !context) return;
      const { image } = picked;
      const { x, y, side } = cropSquare(image.width, image.height, this.crop());
      context.clearRect(0, 0, CROP_SIZE, CROP_SIZE);
      context.drawImage(image, x, y, side, side, 0, 0, CROP_SIZE, CROP_SIZE);
    });
  }

  protected saveName(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.nameStatus.set('saving');
    this.nameError.set(null);
    this.api.changeDisplayName(this.form.getRawValue().displayName).subscribe({
      next: (user) => {
        this.session.update(user);
        this.form.reset({ displayName: user.displayName });
        this.nameStatus.set('saved');
      },
      error: (err: HttpErrorResponse) => {
        this.nameStatus.set('idle');
        const message: string | undefined =
          err.status === 400 ? err.error?.errors?.displayName : undefined;
        if (message) {
          this.form.controls.displayName.setErrors({ server: message });
          this.form.controls.displayName.markAsTouched();
        } else {
          this.nameError.set(
            err.status === 429
              ? tooMany(err)
              : "We couldn't save your name. Check your connection and try again.",
          );
        }
      },
    });
  }

  /** The message to show under the name, or null while it is valid or not visited yet. */
  protected nameErrorText(): string | null {
    const control = this.form.controls.displayName;
    if (!control.touched || !control.errors) return null;
    if (control.hasError('server')) return control.getError('server');
    return DISPLAY_NAME_MESSAGES[Object.keys(control.errors)[0]] ?? null;
  }

  protected choosePreset(preset: AvatarPreset): void {
    this.saveAvatar(this.api.choosePreset(preset));
  }

  /** Checks the picked file and shows it as a preview; nothing is sent until the member confirms. */
  protected pick(input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = ''; // so picking the same file again still fires change
    if (!file) return;
    this.clearPicked();
    if (!UPLOAD_TYPES.includes(file.type)) {
      this.avatarError.set('Choose a JPEG, PNG or WebP image.');
      return;
    }
    if (file.size > UPLOAD_MAX_BYTES) {
      this.avatarError.set('The picture must be 5 MB or smaller.');
      return;
    }
    this.avatarError.set(null);
    const pick = this.pickCount;
    createImageBitmap(file).then(
      (image) => {
        if (pick !== this.pickCount) return image.close();
        this.crop.set(centeredCrop(image.width, image.height));
        this.picked.set({ file, image });
      },
      () => {
        if (pick === this.pickCount)
          this.avatarError.set('The file could not be read as an image.');
      },
    );
  }

  /** Zoom keeps the centre of the circle where it is. */
  protected zoomTo(zoom: number): void {
    this.moveBy(0, 0, zoom);
  }

  protected startDrag(event: PointerEvent): void {
    (event.target as HTMLElement).setPointerCapture(event.pointerId);
    this.dragFrom = { x: event.clientX, y: event.clientY };
  }

  protected drag(event: PointerEvent): void {
    const canvas = this.canvas()?.nativeElement;
    const picked = this.picked();
    if (!this.dragFrom || !canvas || !picked) return;
    const { side } = cropSquare(picked.image.width, picked.image.height, this.crop());
    // Image pixels per CSS pixel; moving the picture right shows more of its left side.
    const scale = side / (canvas.clientWidth || CROP_SIZE);
    this.moveBy(
      (this.dragFrom.x - event.clientX) * scale,
      (this.dragFrom.y - event.clientY) * scale,
    );
    this.dragFrom = { x: event.clientX, y: event.clientY };
  }

  protected endDrag(): void {
    this.dragFrom = null;
  }

  /** Arrow keys move the picture like a drag, a tenth of the circle per press. */
  protected nudge(event: KeyboardEvent): void {
    const step = { ArrowLeft: [1, 0], ArrowRight: [-1, 0], ArrowUp: [0, 1], ArrowDown: [0, -1] }[
      event.key
    ];
    const picked = this.picked();
    if (!step || !picked) return;
    event.preventDefault();
    const { side } = cropSquare(picked.image.width, picked.image.height, this.crop());
    this.moveBy((step[0] * side) / 10, (step[1] * side) / 10);
  }

  private moveBy(dx: number, dy: number, zoom = this.crop().zoom): void {
    const picked = this.picked();
    if (!picked) return;
    this.crop.set(moveCrop(picked.image.width, picked.image.height, this.crop(), dx, dy, zoom));
  }

  /** Uploads the cropped square in the picked file's format (browsers without WebP encoding send PNG). */
  protected upload(): void {
    const picked = this.picked();
    const canvas = this.canvas()?.nativeElement;
    if (!picked || !canvas) return;
    this.avatarBusy.set(true);
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          this.avatarBusy.set(false);
          this.avatarError.set("We couldn't prepare your picture. Try another one.");
          return;
        }
        const cropped = new File([blob], picked.file.name, { type: blob.type });
        this.saveAvatar(this.api.uploadAvatar(cropped));
      },
      picked.file.type,
      0.92,
    );
  }

  protected cancelPick(): void {
    this.clearPicked();
  }

  protected removeUpload(): void {
    this.saveAvatar(this.api.removeAvatar());
  }

  private saveAvatar(request: Observable<CurrentUser>): void {
    this.avatarBusy.set(true);
    this.avatarError.set(null);
    request.subscribe({
      next: (user) => {
        this.avatarBusy.set(false);
        this.session.update(user);
        this.clearPicked();
      },
      error: (err: HttpErrorResponse) => {
        this.avatarBusy.set(false);
        this.avatarError.set(avatarErrorText(err));
      },
    });
  }

  private clearPicked(): void {
    this.pickCount++;
    this.dragFrom = null;
    this.picked()?.image.close();
    this.picked.set(null);
  }
}

const tooMany = (err: HttpErrorResponse) =>
  `Too many attempts. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`;

function avatarErrorText(err: HttpErrorResponse): string {
  switch (err.status) {
    case 400:
      // The backend names the problem: not an image, an unsupported type, too many pixels.
      return err.error?.detail ?? 'Choose a JPEG, PNG or WebP image.';
    case 413:
      return 'The picture must be 5 MB or smaller.';
    case 429:
      return tooMany(err);
    case 503:
      return 'Picture uploads are not available right now. You can pick an avatar instead.';
    default:
      return "We couldn't save your avatar. Check your connection and try again.";
  }
}
