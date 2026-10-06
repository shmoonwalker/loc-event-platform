import { HttpErrorResponse } from '@angular/common/http';
import { Resource } from '@angular/core';

/**
 * The resource's value, or undefined while loading OR after an error.
 * In Angular 22 calling resource.value() in the error state THROWS, and a throwing computed()
 * breaks the whole template. Guarding with hasValue() keeps one failed request from taking the page down.
 */
export function valueOf<T>(resource: Resource<T | undefined>): T | undefined {
  return resource.hasValue() ? resource.value() : undefined;
}

/**
 * The HTTP status the resource failed with (0 = network error), or undefined when it did not fail over HTTP.
 * rxResource may wrap the HttpErrorResponse, so look at `cause` too.
 */
export function errorStatus(resource: Resource<unknown>): number | undefined {
  const e = resource.error();
  const cause = (e as { cause?: unknown } | undefined)?.cause ?? e;
  return cause instanceof HttpErrorResponse ? cause.status : undefined;
}
