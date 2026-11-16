import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AVATAR_PRESETS, AvatarPreset, CurrentUser } from '../../core/auth-api';
import { AuthSession } from '../../core/auth-session';
import { DISPLAY_NAME_MESSAGES, displayNameValidators } from '../../core/display-name';
import { ProfileApi } from '../../core/profile-api';

/** The backend's upload limits, checked here first so a wrong file never leaves the browser. */
const UPLOAD_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const UPLOAD_MAX_BYTES = 5 * 1024 * 1024;

/** The image of a preset avatar; the backend uses the same path. */
export const presetUrl = (preset: AvatarPreset) => `/avatars/${preset.toLowerCase()}.svg`;

/**
 * The "Profile" section of the account settings: the display name and the avatar (a preset, or an uploaded picture).
 * Every change hands the returned account to AuthSession, so the header updates at once.
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

  /** The picked file and its local preview URL, until it is uploaded or cancelled. */
  protected readonly picked = signal<{ file: File; url: string } | null>(null);
  protected readonly avatarBusy = signal(false);
  protected readonly avatarError = signal<string | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.clearPicked());
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
    this.picked.set({ file, url: URL.createObjectURL(file) });
  }

  protected upload(): void {
    const picked = this.picked();
    if (picked) this.saveAvatar(this.api.uploadAvatar(picked.file));
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
    const picked = this.picked();
    if (picked) URL.revokeObjectURL(picked.url);
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
