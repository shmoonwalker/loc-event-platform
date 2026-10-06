import { RailMode } from '../../core/models';

/** The backend only says WHICH rail it is; the heading and subtitle are a frontend concern. */
export function railText(mode: RailMode, cityName: string): { title: string; subtitle: string } {
  switch (mode) {
    case 'TONIGHT':
      return { title: 'Tonight', subtitle: 'In person, starting from 18:00 today' };
    case 'WEEKEND':
      return { title: 'This weekend', subtitle: 'In person, Saturday and Sunday' };
    case 'NEAR_YOU':
      return { title: `Near ${cityName}`, subtitle: `In person in ${cityName}, coming up` };
    case 'ONLINE':
      return { title: 'Online', subtitle: 'Join from anywhere' };
  }
}
