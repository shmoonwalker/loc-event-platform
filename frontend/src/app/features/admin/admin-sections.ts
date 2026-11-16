/** One page of the admin panel: a link in the panel's navigation and a card on the overview. */
export interface AdminSection {
  /** Under /admin. */
  path: string;
  label: string;
  description: string;
}

export const ADMIN_SECTIONS: readonly AdminSection[] = [
  {
    path: 'reports',
    label: 'Reports',
    description: 'Open reports from members: hide the event or dismiss the reports.',
  },
  {
    path: 'hidden-events',
    label: 'Hidden events',
    description: 'Events taken out of public lists, who hid them and why.',
  },
  {
    path: 'feedback',
    label: 'Feedback',
    description: 'Website feedback from visitors and members: mark it handled.',
  },
];
