import { Weather } from '../../core/models';
import { weatherView } from './weather';

const w: Weather = {
  forecastFetchedAt: '2026-10-06T06:00:00Z',
  forecastHour: '2026-10-10T18:00:00Z',
  temperatureCelsius: 14.6,
  precipitationProbabilityPercent: 40,
  windSpeedKmh: 18.2,
  weatherCode: 63,
};

describe('weatherView', () => {
  it('maps the WMO code and rounds the numbers', () => {
    expect(weatherView(w)).toEqual({
      icon: 'rain',
      summary: '15°C, Rain',
      details: '40% chance of rain · Wind 18 km/h',
      hour: '20:00',
    });
  });

  it('keeps the numbers when the code is unknown', () => {
    expect(weatherView({ ...w, weatherCode: 42 })).toMatchObject({
      icon: 'cloud',
      summary: '15°C',
    });
  });

  it('moves the details up when there is no temperature or label', () => {
    expect(weatherView({ ...w, temperatureCelsius: null, weatherCode: null })).toMatchObject({
      summary: '40% chance of rain · Wind 18 km/h',
      details: '',
    });
  });

  it('is null when there is no forecast or nothing usable in it', () => {
    expect(weatherView(null)).toBeNull();
    const empty = {
      ...w,
      temperatureCelsius: null,
      precipitationProbabilityPercent: null,
      windSpeedKmh: null,
      weatherCode: null,
    };
    expect(weatherView(empty)).toBeNull();
  });
});
