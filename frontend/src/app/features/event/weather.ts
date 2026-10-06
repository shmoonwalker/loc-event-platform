import { Weather } from '../../core/models';
import { formatTime } from '../../core/time';
import { IconName } from '../../shared/icon/icon';

export interface WeatherView {
  icon: IconName;
  summary: string; // "15°C, Rain"
  details: string; // "40% chance of rain · Wind 18 km/h", may be empty
  hour: string; // "20:00", the forecast hour in Amsterdam time
}

// WMO weather interpretation codes, grouped into what a visitor cares about.
const GROUPS: [codes: number[], label: string, icon: IconName][] = [
  [[0], 'Clear sky', 'sun'],
  [[1], 'Mainly clear', 'sun'],
  [[2], 'Partly cloudy', 'cloud'],
  [[3], 'Overcast', 'cloud'],
  [[45, 48], 'Fog', 'fog'],
  [[51, 53, 55, 56, 57], 'Drizzle', 'rain'],
  [[61, 63, 65, 66, 67, 80, 81, 82], 'Rain', 'rain'],
  [[71, 73, 75, 77, 85, 86], 'Snow', 'snow'],
  [[95, 96, 99], 'Thunderstorm', 'storm'],
];

/** The forecast as display strings, or null when there is nothing worth showing. */
export function weatherView(w: Weather | null): WeatherView | null {
  if (!w) return null;
  const group = GROUPS.find(([codes]) => w.weatherCode !== null && codes.includes(w.weatherCode));
  const summary = [
    w.temperatureCelsius === null ? null : `${Math.round(w.temperatureCelsius)}°C`,
    group?.[1],
  ]
    .filter(Boolean)
    .join(', ');
  const details = [
    w.precipitationProbabilityPercent === null
      ? null
      : `${w.precipitationProbabilityPercent}% chance of rain`,
    w.windSpeedKmh === null ? null : `Wind ${Math.round(w.windSpeedKmh)} km/h`,
  ]
    .filter(Boolean)
    .join(' · ');
  if (!summary && !details) return null;
  return {
    icon: group?.[2] ?? 'cloud',
    summary: summary || details,
    details: summary ? details : '',
    hour: formatTime(w.forecastHour),
  };
}
