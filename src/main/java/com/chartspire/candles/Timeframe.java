package com.chartspire.candles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A candle interval: a unit and a positive count, written like {@code 15m} or {@code 3d}.
 *
 * <p>Equality is by unit and count, so {@code 60m} and {@code 1h} are different values even though
 * their buckets are identical.
 *
 * <p>Input timestamps must lie in {@link #MIN_SUPPORTED_MILLIS} to {@link
 * #MAX_SUPPORTED_MILLIS_EXCLUSIVE} (years 0000 to 9999); within it no arithmetic overflows. The
 * limit applies to inputs only: a returned boundary can fall outside it, such as the week bucket
 * containing 0000-01-01, which opens on -0001-12-27. Passing such a boundary back in throws.
 */
public record Timeframe(Unit unit, int count) {

    private static final Pattern TEXT = Pattern.compile("([1-9][0-9]{0,9})([mhdwMy])");
    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final long WEEK_MS = 7 * DAY_MS;
    private static final long MONDAY_AFTER_EPOCH = 4 * DAY_MS;
    private static final long GREGORIAN_CYCLE_MS = 146_097 * DAY_MS;

    /** Earliest supported timestamp, 0000-01-01T00:00:00Z. */
    public static final long MIN_SUPPORTED_MILLIS = -62_167_219_200_000L;

    /** End of the supported range (exclusive), 10000-01-01T00:00:00Z. */
    public static final long MAX_SUPPORTED_MILLIS_EXCLUSIVE = 253_402_300_800_000L;

    public enum Unit {
        MINUTE("m"),
        HOUR("h"),
        DAY("d"),
        WEEK("w"),
        MONTH("M"),
        YEAR("y");

        private final String suffix;

        Unit(String suffix) {
            this.suffix = suffix;
        }
    }

    /** A half-open UTC range {@code [from, toExclusive)} in epoch millis. */
    public record Range(long from, long toExclusive) {}

    public Timeframe {
        Objects.requireNonNull(unit, "unit");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        if (unit == Unit.MONTH && 12 % count != 0) {
            throw new IllegalArgumentException("month count must divide 12: " + count);
        }
        if (unit == Unit.YEAR && count != 1) {
            throw new IllegalArgumentException("year count must be 1: " + count);
        }
    }

    /**
     * Parses {@code <count><unit>}, where the unit is {@code m}, {@code h}, {@code d}, {@code w},
     * {@code M} or {@code y} (only {@code 1y}).
     */
    public static Timeframe parse(String text) {
        Matcher matcher = TEXT.matcher(text == null ? "" : text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid timeframe: " + text);
        }
        long count = Long.parseLong(matcher.group(1));
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("timeframe count too large: " + text);
        }
        return new Timeframe(unit(matcher.group(2)), (int) count);
    }

    /**
     * UTC open time of the bucket containing {@code epochMillis}. Minutes, hours and days count
     * from 1970-01-01, weeks from Monday 1970-01-05, and months and years from January of each
     * year.
     *
     * @throws IllegalArgumentException if {@code epochMillis} is outside the supported range; the
     *     result itself may be earlier than {@link #MIN_SUPPORTED_MILLIS}
     */
    public long bucketOpen(long epochMillis) {
        requireSupported(epochMillis);
        if (isCalendar()) {
            return monthStart(alignedMonthIndex(epochMillis));
        }
        long anchor = anchor();
        long length = fixedMillis();
        return Math.floorDiv(epochMillis - anchor, length) * length + anchor;
    }

    /**
     * UTC open time of the bucket after the one containing {@code epochMillis}.
     *
     * @throws IllegalArgumentException if {@code epochMillis} is outside the supported range; the
     *     result itself may be at or after {@link #MAX_SUPPORTED_MILLIS_EXCLUSIVE}
     */
    public long nextBucketOpen(long epochMillis) {
        requireSupported(epochMillis);
        if (isCalendar()) {
            return monthStart(alignedMonthIndex(epochMillis) + monthSpan());
        }
        return bucketOpen(epochMillis) + fixedMillis();
    }

    /**
     * True when every {@code source} bucket falls inside exactly one bucket of this timeframe, so
     * this timeframe's candles can be built from {@code source} candles (5m into 15m, 1d into 3d).
     */
    public boolean canBuildFrom(Timeframe source) {
        if (source == null) {
            return false;
        }
        if (source.isCalendar()) {
            if (isCalendar()) {
                return monthSpan() % source.monthSpan() == 0;
            }
            // Epoch-counted multiples of the 400-year Gregorian cycle always open on January 1st.
            return unit != Unit.WEEK && fixedMillis() % GREGORIAN_CYCLE_MS == 0;
        }
        long sourceLength = source.fixedMillis();
        if (isCalendar()) {
            return DAY_MS % sourceLength == 0;
        }
        long anchorShift = anchor() - source.anchor();
        return fixedMillis() % sourceLength == 0 && Math.floorMod(anchorShift, sourceLength) == 0;
    }

    /**
     * Widens {@code [from, to]} (both inclusive) to whole buckets of this timeframe. Fetching
     * source candles over the result gives no partial first or last bucket, apart from missing
     * data.
     */
    public Range alignRange(long from, long to) {
        if (from > to) {
            throw new IllegalArgumentException("from " + from + " is after to " + to);
        }
        return new Range(bucketOpen(from), nextBucketOpen(to));
    }

    private static void requireSupported(long epochMillis) {
        if (epochMillis < MIN_SUPPORTED_MILLIS || epochMillis >= MAX_SUPPORTED_MILLIS_EXCLUSIVE) {
            throw new IllegalArgumentException(
                    "timestamp "
                            + epochMillis
                            + " is outside the supported range (years 0000-9999)");
        }
    }

    private long anchor() {
        return unit == Unit.WEEK ? MONDAY_AFTER_EPOCH : 0;
    }

    /** Months and years have calendar buckets, not fixed lengths. */
    private boolean isCalendar() {
        return unit == Unit.MONTH || unit == Unit.YEAR;
    }

    private int monthSpan() {
        return unit == Unit.YEAR ? 12 * count : count;
    }

    /** Bucket length in millis; months and years have no fixed length. */
    private long fixedMillis() {
        return switch (unit) {
            case MINUTE -> count * MINUTE_MS;
            case HOUR -> count * HOUR_MS;
            case DAY -> count * DAY_MS;
            case WEEK -> count * WEEK_MS;
            case MONTH, YEAR -> throw new IllegalStateException(unit + " has no fixed length");
        };
    }

    /** Months since year 0 of the bucket containing {@code epochMillis}. */
    private long alignedMonthIndex(long epochMillis) {
        LocalDate date = Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).toLocalDate();
        long monthIndex = date.getYear() * 12L + date.getMonthValue() - 1;
        return Math.floorDiv(monthIndex, monthSpan()) * monthSpan();
    }

    private static long monthStart(long monthIndex) {
        int year = Math.toIntExact(Math.floorDiv(monthIndex, 12L));
        int month = (int) Math.floorMod(monthIndex, 12L) + 1;
        return LocalDate.of(year, month, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static Unit unit(String suffix) {
        for (Unit unit : Unit.values()) {
            if (unit.suffix.equals(suffix)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("invalid timeframe unit: " + suffix);
    }

    @Override
    public String toString() {
        return count + unit.suffix;
    }
}
