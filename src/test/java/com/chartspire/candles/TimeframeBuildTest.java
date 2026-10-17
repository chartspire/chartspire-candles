package com.chartspire.candles;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeframeBuildTest {

    private static final List<String> ALL =
            List.of(
                    "1m", "3m", "5m", "7m", "10m", "15m", "30m", "45m", "60m", "1h", "2h", "4h",
                    "5h", "6h", "8h", "12h", "1d", "2d", "3d", "7d", "1w", "2w", "1M", "2M", "3M",
                    "4M", "6M", "12M", "1y");

    @ParameterizedTest(name = "{1} from {0}: {2}")
    @CsvSource({
        "1m, 10m, true",
        "1m, 15m, true",
        "1m, 3d, true",
        "1m, 1w, true",
        "1m, 1M, true",
        "5m, 10m, true",
        "5m, 15m, true",
        "5m, 30m, true",
        "5m, 1h, true",
        "5m, 12h, true",
        "5m, 3d, true",
        "15m, 1h, true",
        "1h, 4h, true",
        "1h, 6h, true",
        "4h, 12h, true",
        "4h, 1d, true",
        "1d, 3d, true",
        "1d, 1w, true",
        "1d, 1M, true",
        "1w, 2w, true",
        "1M, 3M, true",
        "3M, 12M, true",
        "1M, 1y, true",
        "6M, 1y, true",
        "12M, 1y, true",
        "1y, 12M, true",
        "1d, 1y, true",
        "1m, 1y, true",
        "1y, 6M, false",
        "1y, 1d, false",
        "1w, 1y, false",
        "1y, 146097d, true",
        "1y, 20871w, false",
        "1h, 60m, true",
        "1h, 1h, true",
        // not exact multiples
        "5m, 7m, false",
        "10m, 15m, false",
        "15m, 10m, false",
        "4h, 6h, false",
        "2d, 3d, false",
        // smaller targets
        "1h, 15m, false",
        "1d, 4h, false",
        // bucket edges do not line up
        "3d, 1w, false",
        "2d, 1w, false",
        "1w, 1M, false",
        "3d, 1M, false",
        "2M, 3M, false",
        "1M, 1w, false",
        "1M, 1d, false",
        "3M, 4M, false",
        "1d, 4M, true",
        "4M, 12M, true",
        // 1970-01-01 is a Thursday, so epoch-counted 7d buckets are not Monday weeks
        "7d, 1w, false",
        "1d, 7d, true",
        // The Gregorian calendar repeats every 146097 days (400 years), so epoch-counted
        // multiples of it always open on January 1st, which is a month boundary
        "1M, 146097d, true",
        "3M, 292194d, true",
        "12M, 146097d, true",
        "1M, 146096d, false",
        "1M, 3506328h, true",
        // 146097 days is also 20871 weeks, but weeks open on Mondays, not January 1st
        "1M, 20871w, false",
    })
    void canBuildFrom(String source, String target, boolean expected) {
        assertThat(Timeframe.parse(target).canBuildFrom(Timeframe.parse(source)))
                .isEqualTo(expected);
    }

    /** Every target boundary must also be a source boundary, checked over real UTC time. */
    @Test
    void agreesWithBruteForceOverRealTime() {
        long from = Instant.parse("2023-12-01T00:00:00Z").toEpochMilli();
        long to = Instant.parse("2025-03-01T00:00:00Z").toEpochMilli();
        List<Timeframe> all = ALL.stream().map(Timeframe::parse).toList();

        for (Timeframe source : all) {
            for (Timeframe target : all) {
                boolean nests = everyTargetOpenIsSourceOpen(source, target, from, to);
                assertThat(target.canBuildFrom(source))
                        .as("%s from %s", target, source)
                        .isEqualTo(nests);
            }
        }
    }

    @Test
    void nullSourceCannotBuild() {
        assertThat(Stream.of("1m", "1w", "1M").map(Timeframe::parse))
                .allSatisfy(target -> assertThat(target.canBuildFrom(null)).isFalse());
    }

    private static boolean everyTargetOpenIsSourceOpen(
            Timeframe source, Timeframe target, long from, long to) {
        for (long open = target.bucketOpen(from); open < to; open = target.nextBucketOpen(open)) {
            if (source.bucketOpen(open) != open) {
                return false;
            }
        }
        return true;
    }
}
