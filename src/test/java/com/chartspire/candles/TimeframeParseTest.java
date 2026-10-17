package com.chartspire.candles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chartspire.candles.Timeframe.Unit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TimeframeParseTest {

    @ParameterizedTest
    @CsvSource({
        "1m, MINUTE, 1",
        "5m, MINUTE, 5",
        "15m, MINUTE, 15",
        "1h, HOUR, 1",
        "12h, HOUR, 12",
        "1d, DAY, 1",
        "3d, DAY, 3",
        "1w, WEEK, 1",
        "1M, MONTH, 1",
        "3M, MONTH, 3",
        "12M, MONTH, 12",
    })
    void parsesUnitAndCount(String text, Unit unit, int count) {
        Timeframe timeframe = Timeframe.parse(text);

        assertThat(timeframe.unit()).isEqualTo(unit);
        assertThat(timeframe.count()).isEqualTo(count);
        assertThat(timeframe).hasToString(text);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "m",
                "0m",
                "-1m",
                "1",
                "1s",
                "1H",
                "1D",
                "1W",
                "1y",
                " 1m",
                "1m ",
                "1.5h",
                "01m",
                "99999999999m"
            })
    void rejectsMalformedText(String text) {
        assertThatThrownBy(() -> Timeframe.parse(text))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"5M", "7M", "24M"})
    void monthCountMustDivideAYear(String text) {
        assertThatThrownBy(() -> Timeframe.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("divide 12");
    }

    @Test
    void constructorRejectsNonPositiveCountAndNullUnit() {
        assertThatThrownBy(() -> new Timeframe(Unit.MINUTE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Timeframe(null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalTextGivesEqualTimeframes() {
        assertThat(Timeframe.parse("4h")).isEqualTo(new Timeframe(Unit.HOUR, 4));
    }

    @Test
    void differentSpellingsOfTheSameBucketsAreNotEqual() {
        Timeframe minutes = Timeframe.parse("60m");
        Timeframe hour = Timeframe.parse("1h");

        assertThat(minutes).isNotEqualTo(hour);
        assertThat(minutes.bucketOpen(5_000_000L)).isEqualTo(hour.bucketOpen(5_000_000L));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"2147483647m", "2147483647h", "2147483647d", "2147483647w", "1000000000m"})
    void parsesTheFullPositiveIntRange(String text) {
        assertThat(Timeframe.parse(text)).hasToString(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648m", "9999999999m"})
    void rejectsCountsAboveIntRange(String text) {
        assertThatThrownBy(() -> Timeframe.parse(text))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"MINUTE, 2147483647", "WEEK, 2147483647", "MINUTE, 1000000000", "MONTH, 12"})
    void constructedTimeframesRoundTripThroughText(Unit unit, int count) {
        Timeframe timeframe = new Timeframe(unit, count);

        assertThat(Timeframe.parse(timeframe.toString())).isEqualTo(timeframe);
    }
}
