# chartspire-candles

Java 25 library that builds higher-timeframe UTC candles from lower-timeframe candles. Crypto
(24/7) only: no sessions, time zones or calendars. Package `com.chartspire.candles`. No runtime
dependencies; tests use JUnit and AssertJ.

## Build

```bash
./mvnw verify
```

Spotless is AOSP (`./mvnw spotless:apply` after Java edits). Merge gate is `mvn verify`.
JaCoCo bundle floor: 95% line / 90% branch. Do not lower it. Distribution is `./mvnw install`
(local repository) for now.

## Rules

- Work test-first: write the failing test, make it pass, then refactor.
- Services call this only for intervals the exchange lacks; native exchange candles win. So a
  built `3d` (epoch-anchored) can differ from another exchange's native `3d` (Binance anchors at
  each symbol's first bar). That is accepted.
- Bucket alignment is fixed: epoch-counted minutes/hours/days, Monday weeks, year-aligned
  months. Changing it changes every consumer's candles.
- Do not commit unless asked.
