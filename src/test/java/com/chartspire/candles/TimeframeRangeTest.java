package com.chartspire.candles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeframeRangeTest {

    @ParameterizedTest(name = "{0} over [{1}, {2}] is [{3}, {4})")
    @CsvSource({
        "4h, 2024-01-01T05:00:00Z, 2024-01-01T09:00:00Z, 2024-01-01T04:00:00Z,"
                + " 2024-01-01T12:00:00Z",
        "4h, 2024-01-01T04:00:00Z, 2024-01-01T04:00:00Z, 2024-01-01T04:00:00Z,"
                + " 2024-01-01T08:00:00Z",
        "4h, 2024-01-01T04:00:00Z, 2024-01-01T07:59:59.999Z, 2024-01-01T04:00:00Z,"
                + " 2024-01-01T08:00:00Z",
        "3d, 2024-01-01T00:00:00Z, 2024-01-05T00:00:00Z, 2023-12-31T00:00:00Z,"
                + " 2024-01-06T00:00:00Z",
        "1M, 2024-02-10T00:00:00Z, 2024-02-29T23:00:00Z, 2024-02-01T00:00:00Z,"
                + " 2024-03-01T00:00:00Z",
    })
    void widensToWholeBuckets(
            String timeframe, Instant from, Instant to, Instant start, Instant end) {
        Timeframe.Range range =
                Timeframe.parse(timeframe).alignRange(from.toEpochMilli(), to.toEpochMilli());

        assertThat(range.from()).isEqualTo(start.toEpochMilli());
        assertThat(range.toExclusive()).isEqualTo(end.toEpochMilli());
    }

    @Test
    void coversTheWholeSupportedRange() {
        long min = Instant.parse("0000-01-01T00:00:00Z").toEpochMilli();
        long maxExclusive = Instant.parse("+10000-01-01T00:00:00Z").toEpochMilli();

        Timeframe.Range range = Timeframe.parse("1m").alignRange(min, maxExclusive - 1);

        assertThat(range).isEqualTo(new Timeframe.Range(min, maxExclusive));
    }

    @Test
    void rejectsEndpointsOutsideTheSupportedRange() {
        assertThatThrownBy(() -> Timeframe.parse("1m").alignRange(0, Long.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported range");
        assertThatThrownBy(() -> Timeframe.parse("1m").alignRange(Long.MIN_VALUE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported range");
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> Timeframe.parse("1h").alignRange(2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
