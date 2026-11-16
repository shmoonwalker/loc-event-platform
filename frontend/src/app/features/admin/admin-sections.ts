/** One page of the admin panel: a link in the panel's navigation and a card on the overview. */
export interface AdminSection {
  /** Under /admin. */
  path: string;
  label: string;
  description: string;
}

export const ADMIN_SECTIONS: readonly AdminSection[] = [];
