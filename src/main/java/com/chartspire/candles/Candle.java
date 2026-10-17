package com.chartspire.candles;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An OHLCV candle opening at {@code openTime} (UTC epoch millis); {@code turnover} is optional.
 *
 * <p>Equality is record equality, so it is scale-sensitive: {@code 1.0} and {@code 1.00} differ.
 * Compare values with {@link BigDecimal#compareTo} when reconciling candles from different sources.
 */
public record Candle(
        long openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal turnover) {

    public Candle {
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(high, "high");
        Objects.requireNonNull(low, "low");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(volume, "volume");
    }
}
