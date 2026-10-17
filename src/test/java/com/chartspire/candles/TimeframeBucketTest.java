package com.chartspire.candles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeframeBucketTest {

    @ParameterizedTest(name = "{0} at {1} opens {2}")
    @CsvSource({
        // minutes and hours
        "1m, 2024-01-01T00:00:59.999Z, 2024-01-01T00:00:00Z",
        "5m, 2024-01-01T00:04:59.999Z, 2024-01-01T00:00:00Z",
        "15m, 2024-01-01T10:44:59Z, 2024-01-01T10:30:00Z",
        "15m, 2024-01-01T10:45:00Z, 2024-01-01T10:45:00Z",
        "1h, 2024-01-01T10:59:59Z, 2024-01-01T10:00:00Z",
        "4h, 2024-03-10T07:59:59Z, 2024-03-10T04:00:00Z",
        "6h, 2024-03-10T17:00:00Z, 2024-03-10T12:00:00Z",
        "12h, 2024-03-10T11:59:59Z, 2024-03-10T00:00:00Z",
        // days, counted from 1970-01-01
        "1d, 2024-01-01T23:59:59.999Z, 2024-01-01T00:00:00Z",
        "3d, 1970-01-01T00:00:00Z, 1970-01-01T00:00:00Z",
        "3d, 1970-01-03T23:59:59Z, 1970-01-01T00:00:00Z",
        "3d, 1970-01-04T00:00:00Z, 1970-01-04T00:00:00Z",
        "3d, 2024-01-01T12:00:00Z, 2023-12-31T00:00:00Z",
        "3d, 2024-01-03T00:00:00Z, 2024-01-03T00:00:00Z",
        // before the epoch
        "1d, 1969-12-31T23:59:00Z, 1969-12-31T00:00:00Z",
        "3d, 1969-12-31T00:00:00Z, 1969-12-29T00:00:00Z",
        "1h, 1969-12-31T23:30:00Z, 1969-12-31T23:00:00Z",
        // weeks start on Monday
        "1w, 2024-01-07T23:59:59Z, 2024-01-01T00:00:00Z",
        "1w, 2024-01-08T00:00:00Z, 2024-01-08T00:00:00Z",
        "1w, 1970-01-01T00:00:00Z, 1969-12-29T00:00:00Z",
        "2w, 1970-01-12T00:00:00Z, 1970-01-05T00:00:00Z",
        "2w, 1970-01-19T00:00:00Z, 1970-01-19T00:00:00Z",
        // calendar months, aligned to the year
        "1M, 2024-02-29T23:59:59Z, 2024-02-01T00:00:00Z",
        "1M, 2024-03-01T00:00:00Z, 2024-03-01T00:00:00Z",
        "3M, 2024-05-15T00:00:00Z, 2024-04-01T00:00:00Z",
        "6M, 2024-06-30T23:59:59Z, 2024-01-01T00:00:00Z",
        "12M, 2024-07-04T00:00:00Z, 2024-01-01T00:00:00Z",
        "1y, 2024-12-31T23:59:59.999Z, 2024-01-01T00:00:00Z",
        "1y, 1969-06-01T00:00:00Z, 1969-01-01T00:00:00Z",
        "6M, 1969-08-01T00:00:00Z, 1969-07-01T00:00:00Z",
    })
    void bucketOpen(String timeframe, Instant at, Instant open) {
        assertThat(Timeframe.parse(timeframe).bucketOpen(at.toEpochMilli()))
                .isEqualTo(open.toEpochMilli());
    }

    /** Supported timestamps: 0000-01-01T00:00Z inclusive to 10000-01-01T00:00Z exclusive. */
    private static final long MIN = Instant.parse("0000-01-01T00:00:00Z").toEpochMilli();

    private static final long MAX_EXCLUSIVE =
            Instant.parse("+10000-01-01T00:00:00Z").toEpochMilli();

    @ParameterizedTest(name = "{0} rejects {1}")
    @CsvSource({
        "1m, -9223372036854775808",
        "1m, 9223372036854775807",
        "1w, -9223372036828800000",
        "1M, -9223372036854775808",
        "1M, 9223372036854775807",
        "1d, -62167219200001",
        "1d, 253402300800000",
    })
    void rejectsTimestampsOutsideTheSupportedRange(String timeframe, long epochMillis) {
        Timeframe tf = Timeframe.parse(timeframe);

        assertThatThrownBy(() -> tf.bucketOpen(epochMillis))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported range");
        assertThatThrownBy(() -> tf.nextBucketOpen(epochMillis))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported range");
    }

    @Test
    void handlesTheEdgesOfTheSupportedRange() {
        Timeframe minute = Timeframe.parse("1m");
        Timeframe month = Timeframe.parse("1M");
        Timeframe quarter = Timeframe.parse("3M");

        assertThat(minute.bucketOpen(MIN)).isEqualTo(MIN);
        assertThat(minute.nextBucketOpen(MAX_EXCLUSIVE - 1)).isEqualTo(MAX_EXCLUSIVE);
        assertThat(month.bucketOpen(MAX_EXCLUSIVE - 1))
                .isEqualTo(Instant.parse("9999-12-01T00:00:00Z").toEpochMilli());
        assertThat(month.nextBucketOpen(MAX_EXCLUSIVE - 1)).isEqualTo(MAX_EXCLUSIVE);
        assertThat(quarter.bucketOpen(Instant.parse("0000-02-15T00:00:00Z").toEpochMilli()))
                .isEqualTo(MIN);
        assertThat(month.nextBucketOpen(MIN))
                .isEqualTo(Instant.parse("0000-02-01T00:00:00Z").toEpochMilli());
    }

    /**
     * The range limits inputs; a returned boundary may fall outside it and is not a valid input.
     */
    @Test
    void returnedBoundariesMayFallOutsideTheSupportedRange() {
        Timeframe week = Timeframe.parse("1w");
        long open = week.bucketOpen(MIN);

        assertThat(open).isEqualTo(Instant.parse("-0001-12-27T00:00:00Z").toEpochMilli());
        assertThat(open).isLessThan(MIN);
        assertThat(week.nextBucketOpen(MIN))
                .isEqualTo(Instant.parse("0000-01-03T00:00:00Z").toEpochMilli());
        assertThatThrownBy(() -> week.nextBucketOpen(open))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported range");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"2147483647m", "2147483647h", "2147483647d", "2147483647w"})
    void largestCountsDoNotOverflowAnywhereInTheSupportedRange(String timeframe) {
        Timeframe tf = Timeframe.parse(timeframe);

        for (long at : new long[] {MIN, -1, 0, 1, MAX_EXCLUSIVE - 1}) {
            long open = tf.bucketOpen(at);
            long next = tf.nextBucketOpen(at);
            assertThat(open).as("open at %d", at).isLessThanOrEqualTo(at);
            assertThat(next).as("next at %d", at).isGreaterThan(at);
        }
    }

    @ParameterizedTest(name = "{0} after {1} opens {2}")
    @CsvSource({
        "15m, 2024-01-01T10:30:00Z, 2024-01-01T10:45:00Z",
        "15m, 2024-01-01T10:44:59Z, 2024-01-01T10:45:00Z",
        "4h, 2024-01-01T20:00:00Z, 2024-01-02T00:00:00Z",
        "1d, 2024-02-28T00:00:00Z, 2024-02-29T00:00:00Z",
        "3d, 2023-12-31T00:00:00Z, 2024-01-03T00:00:00Z",
        "1w, 2024-12-30T00:00:00Z, 2025-01-06T00:00:00Z",
        "1M, 2024-01-31T00:00:00Z, 2024-02-01T00:00:00Z",
        "1M, 2024-12-01T00:00:00Z, 2025-01-01T00:00:00Z",
        "3M, 2024-10-01T00:00:00Z, 2025-01-01T00:00:00Z",
        "12M, 2024-01-01T00:00:00Z, 2025-01-01T00:00:00Z",
        "1y, 2024-07-01T00:00:00Z, 2025-01-01T00:00:00Z",
        "1y, 9999-06-01T00:00:00Z, +10000-01-01T00:00:00Z",
        "1M, 1969-12-15T00:00:00Z, 1970-01-01T00:00:00Z",
    })
    void nextBucketOpen(String timeframe, Instant at, Instant next) {
        assertThat(Timeframe.parse(timeframe).nextBucketOpen(at.toEpochMilli()))
                .isEqualTo(next.toEpochMilli());
    }
}
