package nl.loc.data.weather;

/** Open-Meteo refused the request in a way that repeating it cannot fix. */
public class WeatherRequestRejectedException extends RuntimeException {

    public WeatherRequestRejectedException(String message) {
        super(message);
    }
}
