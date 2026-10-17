package com.chartspire.candles;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Rebuilds Binance BTCUSDT spot bars from Binance's own lower-timeframe bars and requires an exact
 * match with Binance's native bars. Fixtures come from {@code tools/capture-binance-golden.mjs}.
 */
class BinanceGoldenTest {

    private static final String ROOT = "/golden/binance-btcusdt-spot/";

    @ParameterizedTest(name = "{0}: {3} x {2} from {1}")
    @CsvSource({
        "minutes, 1m, 5m, 576",
        "minutes, 1m, 15m, 192",
        "minutes, 1m, 30m, 96",
        "minutes, 1m, 1h, 48",
        "minutes, 1m, 2h, 24",
        "minutes, 1m, 4h, 12",
        "minutes, 1m, 6h, 8",
        "minutes, 1m, 12h, 4",
        "minutes, 1m, 1d, 2",
        "minutes, 5m, 15m, 192",
        "minutes, 5m, 30m, 96",
        "minutes, 5m, 1h, 48",
        "minutes, 5m, 4h, 12",
        "minutes, 15m, 1h, 48",
        "minutes, 30m, 2h, 24",
        "hours, 1h, 4h, 360",
        "hours, 1h, 6h, 240",
        "hours, 1h, 8h, 180",
        "hours, 1h, 12h, 120",
        "hours, 1h, 1d, 60",
        // No 3d row: Binance anchors 3d bars at each symbol's first bar (BTCUSDT 2017-08-17,
        // SOLUSDT 2020-08-11), not at 1970-01-01, so native 3d bars differ by symbol.
        "days, 1d, 1w, 104",
        "days, 1d, 1M, 24",
    })
    void rebuiltBarsMatchNativeBars(
            String set, String sourceText, String targetText, int completeBuckets) {
        Window window = Window.of(set);
        Timeframe source = Timeframe.parse(sourceText);
        Timeframe target = Timeframe.parse(targetText);

        List<Candle> rebuilt =
                inside(window, target, Resampler.resample(source, target, read(set, source)));
        List<Candle> expected = inside(window, target, read(set, target));

        assertThat(expected).as("native %s bars in %s", target, set).hasSize(completeBuckets);
        assertThat(rebuilt.stream().map(BinanceGoldenTest::normalized).toList())
                .isEqualTo(expected.stream().map(BinanceGoldenTest::normalized).toList());
    }

    private record Window(long from, long toExclusive) {
        static Window of(String set) {
            return switch (set) {
                case "minutes" -> between("2024-02-28T00:00:00Z", "2024-03-01T00:00:00Z");
                case "hours" -> between("2024-01-01T00:00:00Z", "2024-03-01T00:00:00Z");
                case "days" -> between("2023-01-01T00:00:00Z", "2025-01-01T00:00:00Z");
                default -> throw new IllegalArgumentException(set);
            };
        }

        static Window between(String from, String toExclusive) {
            return new Window(
                    Instant.parse(from).toEpochMilli(), Instant.parse(toExclusive).toEpochMilli());
        }
    }

    /** Keeps bars whose whole bucket lies inside the captured window. */
    private static List<Candle> inside(Window window, Timeframe timeframe, List<Candle> candles) {
        return candles.stream()
                .filter(c -> c.openTime() >= window.from())
                .filter(c -> timeframe.nextBucketOpen(c.openTime()) <= window.toExclusive())
                .toList();
    }

    private static List<String> normalized(Candle candle) {
        return List.of(
                Long.toString(candle.openTime()),
                plain(candle.open()),
                plain(candle.high()),
                plain(candle.low()),
                plain(candle.close()),
                plain(candle.volume()),
                plain(candle.turnover()));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Reads {@code openTime,open,high,low,close,volume,quoteVolume} rows after a header. */
    private static List<Candle> read(String set, Timeframe timeframe) {
        String path = ROOT + set + "/" + timeframe + ".csv";
        try (InputStream in = BinanceGoldenTest.class.getResourceAsStream(path)) {
            Objects.requireNonNull(in, "missing fixture " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(BinanceGoldenTest::candle)
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
