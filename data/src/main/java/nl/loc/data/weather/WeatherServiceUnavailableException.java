package nl.loc.data.weather;

/** Open-Meteo could not be reached or failed temporarily, so the message deserves a retry. */
public class WeatherServiceUnavailableException extends RuntimeException {

    public WeatherServiceUnavailableException(String message) {
        super(message);
    }

    public WeatherServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
