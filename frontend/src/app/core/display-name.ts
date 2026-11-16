import { Validators } from '@angular/forms';

/** Same rules as the backend's displayName on RegisterRequest and UpdateProfileRequest. */
export const displayNameValidators = [
  Validators.required,
  Validators.pattern(/\S/),
  Validators.maxLength(100),
];

export const DISPLAY_NAME_MESSAGES: Record<string, string> = {
  required: 'Enter your name.',
  pattern: 'Enter your name.',
  maxlength: 'Use at most 100 characters.',
};
