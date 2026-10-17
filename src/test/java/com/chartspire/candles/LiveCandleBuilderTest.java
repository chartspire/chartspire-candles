package com.chartspire.candles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LiveCandleBuilderTest {

    private static final Timeframe M1 = Timeframe.parse("1m");
    private static final Timeframe M5 = Timeframe.parse("5m");
    private static final Timeframe H4 = Timeframe.parse("4h");

    @Test
    void rejectsTimeframesThatCannotBeBuilt() {
        assertThatThrownBy(() -> new LiveCandleBuilder(H4, Timeframe.parse("6h")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6h")
                .hasMessageContaining("4h");
        assertThatThrownBy(() -> new LiveCandleBuilder(null, M5))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LiveCandleBuilder(M1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void laterSnapshotsOfTheSameMinuteReplaceEarlierOnes() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);

        builder.update(candle("10:00", "10", "11", "9", "10.5", "1"), false);
        LiveCandleBuilder.Update update =
                builder.update(candle("10:00", "10", "12", "9", "11", "3"), false);

        assertThat(update.late()).isFalse();
        assertThat(update.closed()).isEmpty();
        assertThat(update.current()).isEqualTo(candle("10:00", "10", "12", "9", "11", "3"));
    }

    @Test
    void completedMinutesAreFoldedIntoTheCurrentCandle() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);

        builder.update(candle("10:00", "10", "12", "9", "11", "2"), true);
        LiveCandleBuilder.Update update =
                builder.update(candle("10:01", "11", "11.5", "8", "8.5", "0.5"), false);

        assertThat(update.current()).isEqualTo(candle("10:00", "10", "12", "8", "8.5", "2.5"));
    }

    @Test
    void firstUpdateOfTheNextBucketClosesTheCurrentCandle() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        for (String minute : new String[] {"10:00", "10:01", "10:02", "10:03", "10:04"}) {
            assertThat(builder.update(candle(minute, "1", "2", "1", "2", "1"), false).closed())
                    .isEmpty();
        }

        LiveCandleBuilder.Update update =
                builder.update(candle("10:05", "2", "3", "2", "3", "1"), false);

        assertThat(update.closed()).containsExactly(candle("10:00", "1", "2", "1", "2", "5"));
        assertThat(update.current()).isEqualTo(candle("10:05", "2", "3", "2", "3", "1"));
    }

    @Test
    void closedLastMinuteOfABucketClosesItImmediately() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        builder.update(candle("10:00", "1", "2", "1", "2", "1"), true);
        builder.update(candle("10:04", "2", "3", "2", "3", "1"), false);

        LiveCandleBuilder.Update closing =
                builder.update(candle("10:04", "2", "4", "2", "4", "2"), true);
        LiveCandleBuilder.Update next =
                builder.update(candle("10:05", "4", "4", "4", "4", "1"), false);

        Candle expected = candle("10:00", "1", "4", "1", "4", "3");
        assertThat(closing.closed()).containsExactly(expected);
        assertThat(closing.current()).isEqualTo(expected);
        assertThat(next.closed()).isEmpty();
        assertThat(next.current()).isEqualTo(candle("10:05", "4", "4", "4", "4", "1"));
    }

    @Test
    void closedMinuteInsideABucketDoesNotCloseIt() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);

        LiveCandleBuilder.Update update =
                builder.update(candle("10:03", "1", "2", "1", "2", "1"), true);

        assertThat(update.closed()).isEmpty();
        assertThat(update.current()).isEqualTo(candle("10:00", "1", "2", "1", "2", "1"));
    }

    @Test
    void sameTimeframeClosesEveryClosedCandle() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M1);

        LiveCandleBuilder.Update forming =
                builder.update(candle("10:00", "1", "2", "1", "2", "1"), false);
        LiveCandleBuilder.Update closed =
                builder.update(candle("10:00", "1", "3", "1", "3", "2"), true);

        assertThat(forming.closed()).isEmpty();
        assertThat(closed.closed()).containsExactly(candle("10:00", "1", "3", "1", "3", "2"));
    }

    @Test
    void unclosedCandleAndNewOneCanCloseInTheSameUpdate() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M1);
        builder.update(candle("10:00", "1", "2", "1", "2", "1"), false);

        LiveCandleBuilder.Update update =
                builder.update(candle("10:01", "2", "2", "2", "2", "1"), true);

        assertThat(update.closed())
                .containsExactly(
                        candle("10:00", "1", "2", "1", "2", "1"),
                        candle("10:01", "2", "2", "2", "2", "1"));
    }

    @Test
    void lateUpdatesAreDroppedAndReported() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        builder.update(candle("10:00", "1", "2", "1", "2", "1"), false);
        builder.update(candle("10:02", "2", "3", "2", "3", "1"), false);

        LiveCandleBuilder.Update late =
                builder.update(candle("10:01", "9", "9", "9", "9", "9"), true);
        LiveCandleBuilder.Update after =
                builder.update(candle("10:02", "2", "3", "2", "3", "1"), false);

        assertThat(late.late()).isTrue();
        assertThat(late.current()).isNull();
        assertThat(late.closed()).isEmpty();
        assertThat(after.current()).isEqualTo(candle("10:00", "1", "3", "1", "3", "2"));
    }

    @Test
    void updatesForAnAlreadyClosedBucketAreLate() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        builder.update(candle("10:04", "1", "1", "1", "1", "1"), true);

        assertThat(builder.update(candle("10:04", "1", "1", "1", "1", "1"), true).late()).isTrue();
        assertThat(builder.update(candle("10:03", "1", "1", "1", "1", "1"), false).late()).isTrue();
    }

    @Test
    void bucketsWithNoMinutesProduceNothing() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        builder.update(candle("10:00", "1", "1", "1", "1", "1"), false);

        LiveCandleBuilder.Update update =
                builder.update(candle("10:12", "2", "2", "2", "2", "1"), false);

        assertThat(update.closed()).containsExactly(candle("10:00", "1", "1", "1", "1", "1"));
        assertThat(update.current()).isEqualTo(candle("10:10", "2", "2", "2", "2", "1"));
    }

    @Test
    void seedStartsMidBucketAndMinutesItCoversAreLate() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, H4);
        builder.seed(candle("08:00", "100", "120", "90", "110", "50"), millis("10:00"));

        LiveCandleBuilder.Update covered =
                builder.update(candle("09:59", "1", "1", "1", "1", "1"), true);
        LiveCandleBuilder.Update live =
                builder.update(candle("10:00", "110", "130", "105", "125", "5"), false);
        LiveCandleBuilder.Update rollover =
                builder.update(candle("12:00", "125", "125", "125", "125", "1"), false);

        Candle merged = candle("08:00", "100", "130", "90", "125", "55");
        assertThat(covered.late()).isTrue();
        assertThat(live.current()).isEqualTo(merged);
        assertThat(rollover.closed()).containsExactly(merged);
    }

    @Test
    void seededBucketClosesEvenWithoutLiveMinutesInIt() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, H4);
        Candle seed = candle("08:00", "1", "2", "1", "2", "3");
        builder.seed(seed, millis("12:00"));

        assertThat(builder.update(candle("12:00", "2", "2", "2", "2", "1"), false).closed())
                .containsExactly(seed);
    }

    @Test
    void seedIsValidated() {
        Candle seed = candle("08:00", "1", "1", "1", "1", "1");

        assertThatThrownBy(
                        () ->
                                new LiveCandleBuilder(M1, H4)
                                        .seed(
                                                candle("09:00", "1", "1", "1", "1", "1"),
                                                millis("10:00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aligned");
        assertThatThrownBy(() -> new LiveCandleBuilder(M1, H4).seed(seed, millis("08:00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("covered");
        assertThatThrownBy(() -> new LiveCandleBuilder(M1, H4).seed(seed, millis("12:01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("covered");
        assertThatThrownBy(() -> new LiveCandleBuilder(M1, H4).seed(seed, millis("10:00") + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("covered");
        assertThatThrownBy(() -> new LiveCandleBuilder(M1, H4).seed(null, millis("10:00")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void seedIsOnlyAllowedBeforeAnythingElse() {
        Candle seed = candle("08:00", "1", "1", "1", "1", "1");
        LiveCandleBuilder updated = new LiveCandleBuilder(M1, H4);
        updated.update(candle("08:00", "1", "1", "1", "1", "1"), false);
        LiveCandleBuilder seeded = new LiveCandleBuilder(M1, H4);
        seeded.seed(seed, millis("09:00"));

        assertThatThrownBy(() -> updated.seed(seed, millis("09:00")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seeded.seed(seed, millis("09:00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNullAndMisalignedSourceCandles() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);

        assertThatThrownBy(() -> builder.update(null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                builder.update(
                                        new Candle(
                                                millis("10:00") + 1,
                                                BigDecimal.ONE,
                                                BigDecimal.ONE,
                                                BigDecimal.ONE,
                                                BigDecimal.ONE,
                                                BigDecimal.ONE,
                                                null),
                                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aligned");
    }

    private static final Duration SETTLE = Duration.ofSeconds(5);

    @Test
    void closeDueWaitsForTheBucketEndPlusSettle() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        builder.update(candle("10:00", "1", "2", "1", "2", "1"), false);
        builder.update(candle("10:03", "2", "3", "2", "3", "1"), false);

        assertThat(builder.closeDue(millis("10:05") + 4_999, SETTLE)).isEmpty();
        assertThat(builder.closeDue(millis("10:05") + 5_000, SETTLE))
                .contains(candle("10:00", "1", "3", "1", "3", "2"));
        assertThat(builder.closeDue(millis("10:06"), SETTLE)).isEmpty();
    }

    @Test
    void zeroSettleClosesAtTheBucketEnd() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        builder.update(candle("10:04", "1", "1", "1", "1", "1"), false);

        assertThat(builder.closeDue(millis("10:05") - 1, Duration.ZERO)).isEmpty();
        assertThat(builder.closeDue(millis("10:05"), Duration.ZERO)).isPresent();
    }

    @Test
    void updatesForATimerClosedBucketAreLateAndTheNextBucketStartsClean() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        builder.update(candle("10:04", "1", "1", "1", "1", "1"), false);
        builder.closeDue(millis("10:05") + 5_000, SETTLE);

        LiveCandleBuilder.Update late =
                builder.update(candle("10:04", "1", "9", "1", "9", "2"), true);
        LiveCandleBuilder.Update next =
                builder.update(candle("10:05", "2", "2", "2", "2", "1"), false);

        assertThat(late.late()).isTrue();
        assertThat(next.closed()).isEmpty();
        assertThat(next.current()).isEqualTo(candle("10:05", "2", "2", "2", "2", "1"));
    }

    @Test
    void closeDueWithNothingOpenClosesNothing() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        assertThat(builder.closeDue(millis("23:59"), SETTLE)).isEmpty();

        builder.update(candle("10:04", "1", "1", "1", "1", "1"), true);
        assertThat(builder.closeDue(millis("23:59"), SETTLE)).isEmpty();
    }

    @Test
    void closeDueClosesASeededBucket() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, H4);
        Candle seed = candle("08:00", "1", "2", "1", "2", "3");
        builder.seed(seed, millis("10:00"));

        assertThat(builder.closeDue(millis("12:00") + 5_000, SETTLE)).contains(seed);
    }

    @Test
    void closeDueNeverOverflows() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);
        builder.update(candle("10:00", "1", "1", "1", "1", "1"), false);

        assertThat(builder.closeDue(Long.MAX_VALUE, Duration.ofSeconds(Long.MAX_VALUE))).isEmpty();
        assertThat(builder.closeDue(Long.MIN_VALUE, Duration.ZERO)).isEmpty();
        assertThat(builder.closeDue(Long.MAX_VALUE, Duration.ofMillis(Long.MAX_VALUE))).isEmpty();
        assertThat(builder.closeDue(Long.MAX_VALUE, SETTLE)).isPresent();
    }

    @Test
    void closeDueRejectsNegativeOrNullSettle() {
        LiveCandleBuilder builder = new LiveCandleBuilder(M1, M5);

        assertThatThrownBy(() -> builder.closeDue(0, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.closeDue(0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void closedLastDayClosesCalendarBuckets() {
        Timeframe day = Timeframe.parse("1d");
        LiveCandleBuilder month = new LiveCandleBuilder(day, Timeframe.parse("1M"));
        LiveCandleBuilder year = new LiveCandleBuilder(day, Timeframe.parse("1y"));
        Candle lastDayOfFebruary = at("2024-02-29T00:00:00Z", "1");
        Candle lastDayOfYear = at("2024-12-31T00:00:00Z", "1");

        assertThat(month.update(lastDayOfFebruary, true).closed())
                .extracting(Candle::openTime)
                .containsExactly(Instant.parse("2024-02-01T00:00:00Z").toEpochMilli());
        assertThat(year.update(lastDayOfYear, true).closed())
                .extracting(Candle::openTime)
                .containsExactly(Instant.parse("2024-01-01T00:00:00Z").toEpochMilli());
    }

    private static Candle at(String instant, String price) {
        BigDecimal value = new BigDecimal(price);
        return new Candle(
                Instant.parse(instant).toEpochMilli(), value, value, value, value, value, null);
    }

    /** A candle on 2024-01-01 at {@code hhmm} UTC. */
    private static Candle candle(
            String hhmm, String open, String high, String low, String close, String volume) {
        return new Candle(
                millis(hhmm),
                new BigDecimal(open),
                new BigDecimal(high),
                new BigDecimal(low),
                new BigDecimal(close),
                new BigDecimal(volume),
                null);
    }

    private static long millis(String hhmm) {
        return Instant.parse("2024-01-01T" + hhmm + ":00Z").toEpochMilli();
    }
}
