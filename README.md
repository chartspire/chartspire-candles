# chartspire-candles

Builds higher-timeframe candles from lower-timeframe candles for 24/7 (crypto) markets.
All buckets are UTC. Java 25, no runtime dependencies.

Use it only for intervals an exchange does not serve. When the exchange has a native endpoint
for the interval, services return the exchange's own candles and do not call this library.

## Use

```xml
<dependency>
    <groupId>com.chartspire</groupId>
    <artifactId>chartspire-candles</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
Timeframe source = Timeframe.parse("5m");
Timeframe target = Timeframe.parse("4h");

if (!target.canBuildFrom(source)) {
    throw new IllegalArgumentException("cannot build " + target + " from " + source);
}

// Widen the requested window (both ends inclusive) to whole 4h buckets.
Timeframe.Range range = target.alignRange(from, to);

// The range end is exclusive. For an exchange whose endTime is inclusive (Binance, for example),
// pass toExclusive - 1, or you fetch one extra 5m candle that starts a partial 4h bucket.
// Page through the exchange's limit until the whole range is fetched.
List<Candle> fiveMinute = fetchAllPages(source, range.from(), range.toExclusive() - 1);

List<Candle> fourHour = Resampler.resample(source, target, fiveMinute);
```

## Live candles

`LiveCandleBuilder` folds a stream of source candle snapshots (normally the exchange's 1m kline
stream) into one target timeframe, using the same merge as `Resampler`:

```java
LiveCandleBuilder builder = new LiveCandleBuilder(Timeframe.parse("1m"), Timeframe.parse("4h"));

// Optional, when subscribing mid-bucket: the 4h candle so far, built with Resampler from
// closed history candles opening before coveredUntil (leave the forming minute out).
builder.seed(partialFourHour, coveredUntil);

LiveCandleBuilder.Update update = builder.update(minuteSnapshot, minuteIsClosed);
update.closed();   // 4h candles completed by this update, oldest first
update.current();  // latest state of the 4h candle this update touched (null if late)
update.late();     // true when the update was dropped
```

- A later snapshot of the same source candle replaces the earlier one (exchanges resend
  cumulative snapshots).
- A target candle closes on the first update of a later bucket, or at once when the bucket's
  last source candle arrives closed. There is no timer: with no further update, the last
  candle stays open.
- Updates older than the newest accepted source candle, covered by the seed, or for a closed
  bucket are late: dropped and reported, never merged.
- One builder per stream and target; not thread-safe.
- Live: build from 1m. History: build from the largest native interval that divides the target
  (for example `3d` from `1d`).

## Rules

- Timeframes are `<count><unit>`: `m`, `h`, `d`, `w`, `M`, `y` (for example `15m`, `4h`, `3d`, `1w`, `1M`, `1y`).
  A month count must divide 12; the only year is `1y` (same buckets as `12M`).
- Bucket opens: minutes, hours and days count from 1970-01-01 UTC, weeks start on Monday, and
  months are calendar months aligned to the year. Binance anchors `3d` at each symbol's first
  bar instead, so rebuilt `3d` bars differ from Binance's native ones for many symbols.
- `BinanceGoldenTest` checks rebuilt bars against Binance BTCUSDT spot bars captured by
  `tools/capture-binance-golden.mjs` (public data, no key; run it only to refresh fixtures).
- Timestamps must be in years 0000 to 9999 (`Timeframe.MIN_SUPPORTED_MILLIS` to
  `MAX_SUPPORTED_MILLIS_EXCLUSIVE`); outside that, methods throw `IllegalArgumentException`.
  Inside it, no count up to `Integer.MAX_VALUE` overflows. The limit is on inputs only: a
  returned boundary can fall outside it (the `1w` bucket containing 0000-01-01 opens on
  -0001-12-27), and passing that boundary back in throws.
- `target.canBuildFrom(source)` is true only when every source bucket falls inside one target
  bucket: `5m → 15m`, `1h → 4h`, `1d → 3d`, `1d → 1w`, `1d → 1M`, `1M → 3M`. Not `4h → 6h`,
  `3d → 1w`, `7d → 1w` (epoch days start on a Thursday) or `1w → 1M`.
- Equality: `60m` and `1h` are different `Timeframe` values with identical buckets, and `Candle`
  equality is `BigDecimal` scale-sensitive (`1.0` differs from `1.00`).
- `Resampler.resample` merges first open, highest high, lowest low, last close, summed volume.
  Turnover is summed when every candle in the bucket has it, otherwise null. Missing candles
  are skipped; empty buckets are omitted. Input must be aligned to the source timeframe and in
  strictly ascending open-time order.
- The last bucket may still be forming; the caller decides whether to keep it.

## Build

```bash
./mvnw verify    # tests, Spotless (AOSP) check, JaCoCo gate
./mvnw install   # publish to the local Maven repository
```
