import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';

/**
 * Puts a wrong current password and the API's field errors under their fields, and returns
 * anything else as one message for the whole form (null when every error found its field).
 * aliases maps backend field names to form controls: the 72-byte check is a method, not a field.
 */
export function showApiError(
  form: FormGroup,
  err: HttpErrorResponse,
  aliases: Record<string, string> = {},
): string | null {
  if (err.status === 401) {
    return setServerError(form, 'currentPassword', 'Your current password is incorrect.');
  }
  const fieldErrors: Record<string, string> | undefined =
    err.status === 400 ? err.error?.errors : undefined;
  if (fieldErrors) {
    let formError: string | null = null;
    for (const [key, message] of Object.entries(fieldErrors)) {
      formError = setServerError(form, aliases[key] ?? key, message) ?? formError;
    }
    return formError;
  }
  if (err.status === 429) {
    return `Too many attempts. Try again in ${err.headers.get('Retry-After') ?? '60'} seconds.`;
  }
  return "We couldn't save this change. Check your connection and try again.";
}

/** The message to show under a field, or null while it is valid or not visited yet. */
export function fieldError(
  control: AbstractControl,
  messages: Record<string, string>,
): string | null {
  if (!control.touched || !control.errors) return null;
  if (control.hasError('server')) return control.getError('server');
  return messages[Object.keys(control.errors)[0]] ?? null;
}

function setServerError(form: FormGroup, name: string, message: string): string | null {
  const control = form.get(name);
  if (!control) return message;
  control.setErrors({ server: message });
  control.markAsTouched();
  return null;
}
