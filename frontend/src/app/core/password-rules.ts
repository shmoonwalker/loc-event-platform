import { ValidatorFn, Validators } from '@angular/forms';

/** Same rules as the backend's RegisterRequest, used for every new password. */
export const newPasswordValidators: ValidatorFn[] = [
  Validators.required,
  Validators.minLength(10),
  maxBytes(72),
];

/** BCrypt uses only 72 bytes, so the backend limits passwords in bytes; emoji and accents count extra. */
function maxBytes(max: number): ValidatorFn {
  return (control) =>
    new TextEncoder().encode(control.value).length > max ? { maxBytes: true } : null;
}
