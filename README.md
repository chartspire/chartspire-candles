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

## Rules

- Timeframes are `<count><unit>`: `m`, `h`, `d`, `w`, `M` (for example `15m`, `4h`, `3d`, `1w`, `1M`).
  A month count must divide 12.
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

## Release (Maven Central)

Coordinates: `com.chartspire:chartspire-candles`. Licensed under Apache-2.0 (`LICENSE`).

One-time setup:

1. Sign in at central.sonatype.com and verify the `com.chartspire` namespace (DNS TXT record on
   chartspire.com).
2. Create a GPG key, publish the public key to `keyserver.ubuntu.com`, and generate a Central
   user token.
3. Add repository secrets: `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (the user token),
   `GPG_PRIVATE_KEY` (`gpg --armor --export-secret-keys <id>`), `GPG_PASSPHRASE`.

Each release: merge to `main`, then `git tag v0.1.0 && git push origin v0.1.0`. The `release`
workflow sets the version from the tag, runs `verify`, signs, and uploads to Central. The upload
is not auto-published: check it and press Publish in the Central portal. Published versions can
never be changed or deleted.
