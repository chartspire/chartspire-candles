package com.chartspire.candles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds candles of a higher timeframe from candles of a lower one. */
public final class Resampler {

    private Resampler() {}

    /**
     * Merges {@code candles} of {@code source} into {@code target} buckets: first open, highest
     * high, lowest low, last close, summed volume. Turnover is summed when every candle in the
     * bucket has it, otherwise null. Missing candles are skipped; a bucket with none is omitted.
     *
     * @throws IllegalArgumentException if {@code target} cannot be built from {@code source}, or a
     *     candle is not aligned to {@code source} or not in strictly ascending open-time order
     */
    public static List<Candle> resample(Timeframe source, Timeframe target, List<Candle> candles) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(candles, "candles");
        if (!target.canBuildFrom(source)) {
            throw new IllegalArgumentException("cannot build " + target + " from " + source);
        }
        List<Candle> result = new ArrayList<>();
        Candle current = null;
        long previousOpen = Long.MIN_VALUE;
        for (Candle candle : candles) {
            Objects.requireNonNull(candle, "candle");
            long openTime = candle.openTime();
            if (source.bucketOpen(openTime) != openTime) {
                throw new IllegalArgumentException(
                        "candle at " + openTime + " is not aligned to " + source);
            }
            if (current != null && openTime <= previousOpen) {
                throw new IllegalArgumentException("candles must be in ascending open-time order");
            }
            previousOpen = openTime;
            long bucket = target.bucketOpen(openTime);
            if (current != null && current.openTime() == bucket) {
                current = merge(current, candle);
            } else {
                if (current != null) {
                    result.add(current);
                }
                current = withOpenTime(candle, bucket);
            }
        }
        if (current != null) {
            result.add(current);
        }
        return List.copyOf(result);
    }

    private static Candle merge(Candle left, Candle right) {
        return new Candle(
                left.openTime(),
                left.open(),
                left.high().compareTo(right.high()) >= 0 ? left.high() : right.high(),
                left.low().compareTo(right.low()) <= 0 ? left.low() : right.low(),
                right.close(),
                left.volume().add(right.volume()),
                sumOrNull(left.turnover(), right.turnover()));
    }

    private static BigDecimal sumOrNull(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? null : left.add(right);
    }

    private static Candle withOpenTime(Candle candle, long openTime) {
        return new Candle(
                openTime,
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                candle.turnover());
    }
}
