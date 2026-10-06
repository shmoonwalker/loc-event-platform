import { pageWindow } from './page-window';

describe('pageWindow', () => {
  it('shows every page when there are few', () => {
    expect(pageWindow(0, 4)).toEqual([0, 1, 2, 3]);
  });

  it('collapses the middle of a long list', () => {
    expect(pageWindow(5, 20)).toEqual([0, null, 4, 5, 6, null, 19]);
  });

  it('keeps the start tidy', () => {
    expect(pageWindow(1, 20)).toEqual([0, 1, 2, null, 19]);
  });

  it('shows a single missing page instead of an ellipsis', () => {
    expect(pageWindow(3, 20)).toEqual([0, 1, 2, 3, 4, null, 19]);
  });

  it('handles one page and zero pages', () => {
    expect(pageWindow(0, 1)).toEqual([0]);
    expect(pageWindow(0, 0)).toEqual([]);
  });
});
