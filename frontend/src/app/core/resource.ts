import { Resource } from '@angular/core';

/**
 * The resource's value, or undefined while loading OR after an error.
 * In Angular 22 calling resource.value() in the error state THROWS, and a throwing computed()
 * breaks the whole template. Guarding with hasValue() keeps one failed request from taking the page down.
 */
export function valueOf<T>(resource: Resource<T | undefined>): T | undefined {
  return resource.hasValue() ? resource.value() : undefined;
}
