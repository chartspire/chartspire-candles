package com.chartspire.candles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResamplerTest {

    private static final Timeframe M5 = Timeframe.parse("5m");
    private static final Timeframe M15 = Timeframe.parse("15m");
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Test
    void mergesFirstOpenHighestHighLowestLowLastCloseAndSummedVolume() {
        List<Candle> source =
                List.of(
                        candle("2024-01-01T10:00:00Z", "100", "105", "99", "104", "1.5"),
                        candle("2024-01-01T10:05:00Z", "104", "110", "103", "108", "2"),
                        candle("2024-01-01T10:10:00Z", "108", "109", "95", "97", "0.25"),
                        candle("2024-01-01T10:15:00Z", "97", "98", "96", "96.5", "4"));

        List<Candle> result = Resampler.resample(M5, M15, source);

        assertThat(result)
                .containsExactly(
                        candle("2024-01-01T10:00:00Z", "100", "110", "95", "97", "3.75"),
                        candle("2024-01-01T10:15:00Z", "97", "98", "96", "96.5", "4"));
    }

    @Test
    void missingSourceCandlesAreSkippedAndEmptyBucketsProduceNothing() {
        List<Candle> source =
                List.of(
                        candle("2024-01-01T10:00:00Z", "1", "2", "1", "2", "1"),
                        // 10:05 missing
                        candle("2024-01-01T10:10:00Z", "2", "3", "2", "3", "1"),
                        // 10:15 bucket has no candles
                        candle("2024-01-01T10:30:00Z", "3", "4", "3", "4", "1"));

        List<Candle> result = Resampler.resample(M5, M15, source);

        assertThat(result)
                .containsExactly(
                        candle("2024-01-01T10:00:00Z", "1", "3", "1", "3", "2"),
                        candle("2024-01-01T10:30:00Z", "3", "4", "3", "4", "1"));
    }

    @Test
    void partialFirstBucketOpensAtTheTargetBoundary() {
        List<Candle> source =
                List.of(
                        candle("2024-01-01T10:10:00Z", "5", "6", "4", "5", "1"),
                        candle("2024-01-01T10:15:00Z", "5", "7", "5", "7", "1"));

        assertThat(Resampler.resample(M5, M15, source))
                .extracting(Candle::openTime)
                .containsExactly(millis("2024-01-01T10:00:00Z"), millis("2024-01-01T10:15:00Z"));
    }

    @Test
    void sameTimeframeReturnsTheCandles() {
        Timeframe h1 = Timeframe.parse("1h");
        List<Candle> source =
                List.of(
                        candle("2024-01-01T10:00:00Z", "1", "2", "1", "2", "1"),
                        candle("2024-01-01T11:00:00Z", "2", "3", "2", "3", "1"));

        assertThat(Resampler.resample(h1, h1, source)).isEqualTo(source);
    }

    @Test
    void buildsEpochAlignedThreeDayCandlesFromDays() {
        List<Candle> source =
                List.of(
                        candle("2023-12-30T00:00:00Z", "1", "1", "1", "1", "1"),
                        candle("2023-12-31T00:00:00Z", "2", "2", "2", "2", "1"),
                        candle("2024-01-01T00:00:00Z", "3", "3", "3", "3", "1"),
                        candle("2024-01-02T00:00:00Z", "4", "4", "4", "4", "1"),
                        candle("2024-01-03T00:00:00Z", "5", "5", "5", "5", "1"));

        List<Candle> result =
                Resampler.resample(Timeframe.parse("1d"), Timeframe.parse("3d"), source);

        assertThat(result)
                .containsExactly(
                        candle("2023-12-28T00:00:00Z", "1", "1", "1", "1", "1"),
                        candle("2023-12-31T00:00:00Z", "2", "4", "2", "4", "3"),
                        candle("2024-01-03T00:00:00Z", "5", "5", "5", "5", "1"));
    }

    @Test
    void buildsCalendarMonthsFromDays() {
        List<Candle> source =
                List.of(
                        candle("2024-01-31T00:00:00Z", "1", "1", "1", "1", "1"),
                        candle("2024-02-01T00:00:00Z", "2", "2", "2", "2", "1"),
                        candle("2024-02-29T00:00:00Z", "3", "3", "3", "3", "1"),
                        candle("2024-03-01T00:00:00Z", "4", "4", "4", "4", "1"));

        List<Candle> result =
                Resampler.resample(Timeframe.parse("1d"), Timeframe.parse("1M"), source);

        assertThat(result)
                .extracting(Candle::openTime)
                .containsExactly(
                        millis("2024-01-01T00:00:00Z"),
                        millis("2024-02-01T00:00:00Z"),
                        millis("2024-03-01T00:00:00Z"));
        assertThat(result.get(1).volume()).isEqualByComparingTo("2");
    }

    @Test
    void turnoverIsSummedWhenEveryCandleHasIt() {
        List<Candle> source =
                List.of(
                        withTurnover(candle("2024-01-01T10:00:00Z", "1", "1", "1", "1", "1"), "10"),
                        withTurnover(
                                candle("2024-01-01T10:05:00Z", "1", "1", "1", "1", "1"), "2.5"),
                        withTurnover(candle("2024-01-01T10:15:00Z", "1", "1", "1", "1", "1"), "7"),
                        candle("2024-01-01T10:20:00Z", "1", "1", "1", "1", "1"));

        List<Candle> result = Resampler.resample(M5, M15, source);

        assertThat(result.get(0).turnover()).isEqualByComparingTo("12.5");
        assertThat(result.get(1).turnover()).isNull();
    }

    @Test
    void anyMissingTurnoverInABucketGivesNullAndTheNextBucketRecovers() {
        List<Candle> source =
                List.of(
                        // [null, 5]
                        candle("2024-01-01T10:00:00Z", "1", "1", "1", "1", "1"),
                        withTurnover(candle("2024-01-01T10:05:00Z", "1", "1", "1", "1", "1"), "5"),
                        // [5, null, 7]
                        withTurnover(candle("2024-01-01T10:15:00Z", "1", "1", "1", "1", "1"), "5"),
                        candle("2024-01-01T10:20:00Z", "1", "1", "1", "1", "1"),
                        withTurnover(candle("2024-01-01T10:25:00Z", "1", "1", "1", "1", "1"), "7"),
                        // [3, 4]
                        withTurnover(candle("2024-01-01T10:30:00Z", "1", "1", "1", "1", "1"), "3"),
                        withTurnover(candle("2024-01-01T10:35:00Z", "1", "1", "1", "1", "1"), "4"));

        List<Candle> result = Resampler.resample(M5, M15, source);

        assertThat(result)
                .extracting(Candle::turnover)
                .containsExactly(null, null, new BigDecimal("7"));
    }

    @Test
    void candleEqualityIsScaleSensitive() {
        Candle one = candle("2024-01-01T10:00:00Z", "1.0", "1.0", "1.0", "1.0", "1.0");
        Candle sameValue = candle("2024-01-01T10:00:00Z", "1.00", "1.00", "1.00", "1.00", "1.00");

        assertThat(one).isNotEqualTo(sameValue);
    }

    @Test
    void equalHighsAndLowsKeepTheFirstCandlesValue() {
        List<Candle> source =
                List.of(
                        candle("2024-01-01T10:00:00Z", "1", "2.0", "1.0", "1", "1"),
                        candle("2024-01-01T10:05:00Z", "1", "2.00", "1.00", "1", "1"));

        Candle merged = Resampler.resample(M5, M15, source).get(0);

        assertThat(merged.high()).isEqualTo(new BigDecimal("2.0"));
        assertThat(merged.low()).isEqualTo(new BigDecimal("1.0"));
    }

    @Test
    void rejectsCandlesOutsideTheSupportedRange() {
        List<Candle> source = List.of(new Candle(Long.MIN_VALUE, ONE, ONE, ONE, ONE, ONE, null));

        assertThatThrownBy(() -> Resampler.resample(Timeframe.parse("1m"), M5, source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported range");
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertThat(Resampler.resample(M5, M15, List.of())).isEmpty();
    }

    @Test
    void resultIsUnmodifiableAndInputIsUntouched() {
        List<Candle> source =
                new ArrayList<>(
                        List.of(
                                candle("2024-01-01T10:00:00Z", "1", "2", "1", "2", "1"),
                                candle("2024-01-01T10:05:00Z", "2", "3", "2", "3", "1")));
        List<Candle> copy = List.copyOf(source);

        List<Candle> result = Resampler.resample(M5, M15, source);

        assertThat(source).isEqualTo(copy);
        assertThatThrownBy(() -> result.add(copy.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsTimeframesThatCannotBeBuilt() {
        assertThatThrownBy(
                        () ->
                                Resampler.resample(
                                        Timeframe.parse("4h"), Timeframe.parse("6h"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6h")
                .hasMessageContaining("4h");
    }

    @Test
    void rejectsCandlesNotAlignedToTheSourceTimeframe() {
        List<Candle> source = List.of(candle("2024-01-01T10:02:00Z", "1", "1", "1", "1", "1"));

        assertThatThrownBy(() -> Resampler.resample(M5, M15, source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aligned");
    }

    @Test
    void rejectsOutOfOrderAndDuplicateCandles() {
        Candle first = candle("2024-01-01T10:00:00Z", "1", "1", "1", "1", "1");
        Candle second = candle("2024-01-01T10:05:00Z", "1", "1", "1", "1", "1");

        assertThatThrownBy(() -> Resampler.resample(M5, M15, List.of(second, first)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ascending");
        assertThatThrownBy(() -> Resampler.resample(M5, M15, List.of(first, first)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ascending");
    }

    @Test
    void rejectsNulls() {
        Candle candle = candle("2024-01-01T10:00:00Z", "1", "1", "1", "1", "1");

        assertThatThrownBy(() -> Resampler.resample(null, M15, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Resampler.resample(M5, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Resampler.resample(M5, M15, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Resampler.resample(M5, M15, Arrays.asList(candle, null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void candleRequiresPricesAndVolume() {
        BigDecimal one = BigDecimal.ONE;
        assertThatThrownBy(() -> new Candle(0, null, one, one, one, one, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Candle(0, one, null, one, one, one, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Candle(0, one, one, null, one, one, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Candle(0, one, one, one, null, one, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Candle(0, one, one, one, one, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    static Candle candle(
            String openTime, String open, String high, String low, String close, String volume) {
        return new Candle(
                millis(openTime),
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                new BigDecimal(volume),
                null);
    }

    private static Candle withTurnover(Candle candle, String turnover) {
        return new Candle(
                candle.openTime(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume(),
                new BigDecimal(turnover));
    }

    private static long millis(String instant) {
        return Instant.parse(instant).toEpochMilli();
    }
}
