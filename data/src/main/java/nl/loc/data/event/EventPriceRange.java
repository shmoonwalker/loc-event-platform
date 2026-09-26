package nl.loc.data.event;

import java.math.BigDecimal;

/** Source-provided indicative prices; missing values do not mean free admission. */
public record EventPriceRange(BigDecimal min, BigDecimal max, String currency, String type) {
}
