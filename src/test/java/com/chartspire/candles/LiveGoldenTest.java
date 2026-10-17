package com.chartspire.candles;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Replays captured Binance BTCUSDT spot candles as a live stream (a forming snapshot, then the
 * final closed snapshot of each source candle) and requires the live result to equal {@link
 * Resampler}'s historical result. {@code BinanceGoldenTest} ties that result to Binance's own bars.
 */
class LiveGoldenTest {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    @ParameterizedTest(name = "{2} from {1}")
    @CsvSource({
        "minutes, 1m, 1m",
        "minutes, 1m, 2m",
        "minutes, 1m, 3m",
        "minutes, 1m, 5m",
        "minutes, 1m, 10m",
        "minutes, 1m, 15m",
        "minutes, 1m, 30m",
        "minutes, 1m, 1h",
        "minutes, 1m, 2h",
        "minutes, 1m, 4h",
        "minutes, 1m, 6h",
        "minutes, 1m, 8h",
        "minutes, 1m, 12h",
        "minutes, 1m, 1d",
        "days, 1d, 3d",
        "days, 1d, 1w",
        "days, 1d, 1M",
        "days, 1d, 3M",
        "days, 1d, 6M",
        "days, 1d, 1y",
    })
    void liveCandlesEqualHistoricalCandles(String set, String sourceText, String targetText) {
        Timeframe source = Timeframe.parse(sourceText);
        Timeframe target = Timeframe.parse(targetText);
        List<Candle> history = read(set, source);
        LiveCandleBuilder builder = new LiveCandleBuilder(source, target);

        List<Candle> live = new ArrayList<>();
        Candle last = null;
        for (Candle candle : history) {
            for (LiveCandleBuilder.Update update :
                    List.of(builder.update(forming(candle), false), builder.update(candle, true))) {
                assertThat(update.late()).isFalse();
                live.addAll(update.closed());
                last = update.current();
            }
        }
        if (live.isEmpty() || live.getLast().openTime() != last.openTime()) {
            live.add(last);
        }

        assertThat(live).isEqualTo(Resampler.resample(source, target, history));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"5m", "1h", "1d"})
    void currentCandleAfterEveryMinuteEqualsHistoryUpToThatMinute(String targetText) {
        Timeframe source = Timeframe.parse("1m");
        Timeframe target = Timeframe.parse(targetText);
        List<Candle> history = read("minutes", source);
        LiveCandleBuilder builder = new LiveCandleBuilder(source, target);

        int bucketStart = 0;
        for (int i = 0; i < history.size(); i++) {
            Candle candle = history.get(i);
            if (target.bucketOpen(candle.openTime())
                    != target.bucketOpen(history.get(bucketStart).openTime())) {
                bucketStart = i;
            }
            List<Candle> soFar = new ArrayList<>(history.subList(bucketStart, i));
            soFar.add(forming(candle));
            Candle expectedForming = Resampler.resample(source, target, soFar).getFirst();
            soFar.set(soFar.size() - 1, candle);
            Candle expectedFinal = Resampler.resample(source, target, soFar).getFirst();

            assertThat(builder.update(forming(candle), false).current()).isEqualTo(expectedForming);
            assertThat(builder.update(candle, true).current()).isEqualTo(expectedFinal);
        }
    }

    /** An earlier, partial snapshot of {@code candle}: no move yet, half the volume. */
    private static Candle forming(Candle candle) {
        return new Candle(
                candle.openTime(),
                candle.open(),
                candle.open(),
                candle.open(),
                candle.open(),
                candle.volume().divide(TWO),
                candle.turnover() == null ? null : candle.turnover().divide(TWO));
    }

    private static List<Candle> read(String set, Timeframe timeframe) {
        String path = "/golden/binance-btcusdt-spot/" + set + "/" + timeframe + ".csv";
        try (InputStream in = LiveGoldenTest.class.getResourceAsStream(path)) {
            Objects.requireNonNull(in, "missing fixture " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(LiveGoldenTest::candle)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Candle candle(String line) {
        String[] f = line.split(",");
        return new Candle(
                Long.parseLong(f[0]),
                new BigDecimal(f[1]),
                new BigDecimal(f[2]),
                new BigDecimal(f[3]),
                new BigDecimal(f[4]),
                new BigDecimal(f[5]),
                new BigDecimal(f[6]));
    }
}
