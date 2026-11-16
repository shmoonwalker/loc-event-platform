import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReportTarget } from '../../core/reports-api';
import { ReportButton } from './report-button';

async function render(target: ReportTarget = 'event', targetId = 'e1') {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(ReportButton);
  fixture.componentRef.setInput('target', target);
  fixture.componentRef.setInput('targetId', targetId);
  await fixture.whenStable();

  const el = fixture.nativeElement as HTMLElement;
  const http = TestBed.inject(HttpTestingController);
  const dialog = () => el.querySelector('dialog')!;
  const button = (label: string) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === label);
  const tick = () => fixture.detectChanges();
  const openDialog = () => {
    button('Report')!.click();
    tick();
  };
  const choose = (label: string) => {
    const option = [...dialog().querySelectorAll('label')].find((l) =>
      l.textContent?.includes(label),
    )!;
    option.querySelector('input')!.click();
  };
  const describe = (text: string) => {
    const box = dialog().querySelector('textarea')!;
    box.value = text;
    box.dispatchEvent(new Event('input'));
  };
  const submit = () => {
    dialog().querySelector('form')!.dispatchEvent(new Event('submit'));
    tick();
  };
  return { el, http, dialog, button, tick, openDialog, choose, describe, submit };
}

describe('ReportButton', () => {
  beforeEach(() => {
    // jsdom has no modal dialogs.
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
    };
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('opens a modal dialog with the four reasons and optional text', async () => {
    const { dialog, openDialog } = await render();
    expect(dialog().open).toBe(false);

    openDialog();
    expect(dialog().open).toBe(true);
    expect(dialog().querySelector('h2')?.textContent).toContain('Report this event');
    expect(
      [...dialog().querySelectorAll('label.option')].map((l) => l.textContent?.trim()),
    ).toEqual(['Wrong information', 'Spam', 'Inappropriate', 'Something else']);
    expect(dialog().querySelector('textarea')).not.toBeNull();
  });

  it('needs a reason before it sends anything', async () => {
    const { http, dialog, openDialog, submit } = await render();
    openDialog();
    submit();

    http.expectNone(() => true);
    expect(dialog().textContent).toContain('Choose a reason.');
  });

  it('refuses text over 1000 characters', async () => {
    const { http, dialog, openDialog, choose, describe, submit } = await render();
    openDialog();
    choose('Spam');
    describe('x'.repeat(1001));
    submit();

    http.expectNone(() => true);
    expect(dialog().textContent).toContain('Use at most 1000 characters.');
  });

  it('reports the event with the trimmed text, then thanks the member', async () => {
    const { http, dialog, button, tick, openDialog, choose, describe, submit } = await render();
    openDialog();
    choose('Wrong information');
    describe('  The date is last year.  ');
    submit();

    const req = http.expectOne('/api/events/e1/reports');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      reason: 'WRONG_INFORMATION',
      details: 'The date is last year.',
    });
    req.flush({}, { status: 201, statusText: 'Created' });
    tick();

    expect(dialog().querySelector('[role="status"]')?.textContent).toContain('Thanks');
    button('Close')!.click();
    expect(dialog().open).toBe(false);
  });

  it('reports a comment and sends blank text as null', async () => {
    const { http, openDialog, choose, describe, submit } = await render('comment', 'c1');
    openDialog();
    choose('Spam');
    describe('   ');
    submit();

    expect(http.expectOne('/api/comments/c1/reports').request.body).toEqual({
      reason: 'SPAM',
      details: null,
    });
  });

  it.each([
    [429, { 'Retry-After': '300' }, "You've sent a lot of reports. Try again in 300 seconds."],
    [404, {}, 'This event is no longer available.'],
    [0, {}, "We couldn't send your report. Check your connection and try again."],
  ])('shows a %s answer in the dialog', async (status, headers, message) => {
    const { http, dialog, tick, openDialog, choose, submit } = await render();
    openDialog();
    choose('Spam');
    submit();

    http.expectOne('/api/events/e1/reports').flush({}, { status, statusText: 'Error', headers });
    tick();

    expect(dialog().open).toBe(true);
    expect(dialog().querySelector('[role="alert"]')?.textContent?.trim()).toBe(message);
  });

  it('starts empty again when it is opened again', async () => {
    const { http, dialog, button, tick, openDialog, choose, submit } = await render();
    openDialog();
    choose('Spam');
    submit();
    http.expectOne('/api/events/e1/reports').flush({}, { status: 201, statusText: 'Created' });
    tick();
    button('Close')!.click();

    openDialog();
    expect(dialog().querySelector('form')).not.toBeNull();
    expect(dialog().querySelectorAll('input:checked')).toHaveLength(0);
  });

  it('closes with Cancel without sending anything', async () => {
    const { http, dialog, button, openDialog } = await render();
    openDialog();
    button('Cancel')!.click();

    expect(dialog().open).toBe(false);
    http.expectNone(() => true);
  });
});
