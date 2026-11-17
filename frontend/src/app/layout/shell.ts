import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SiteHeader } from './site-header';

/** Page chrome (header, footer, skip link) around whichever page the router puts in <router-outlet>. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, SiteHeader],
  templateUrl: './shell.html',
})
export class Shell {}
