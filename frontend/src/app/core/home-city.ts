import { Injectable, signal } from '@angular/core';

const KEY = 'loc-home-city';
const DEFAULT_CITY = 'amsterdam'; // same default as the backend (City.DEFAULT_SLUG)

/** The visitor's chosen city for the "Near you" rail, remembered between visits. */
@Injectable({ providedIn: 'root' })
export class HomeCity {
  readonly slug = signal(read());

  set(slug: string): void {
    this.slug.set(slug);
    try {
      localStorage.setItem(KEY, slug);
    } catch {
      /* ignore blocked storage */
    }
  }
}

function read(): string {
  try {
    return localStorage.getItem(KEY) || DEFAULT_CITY;
  } catch {
    return DEFAULT_CITY;
  }
}
