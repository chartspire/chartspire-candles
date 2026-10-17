package com.chartspire.candles;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a live higher-timeframe candle from streamed lower-timeframe candle snapshots, such as an
 * exchange's 1m kline stream. Merges exactly like {@link Resampler}, so closed candles equal the
 * historical result for the same source candles.
 *
 * <p>Each update is the exchange's latest full snapshot of a source candle: a later snapshot of the
 * same candle replaces the earlier one. A target candle closes when the first update of a later
 * target bucket arrives, or at once when the bucket's last source candle arrives closed. Updates
 * older than the newest accepted source candle, or for a closed bucket, are late: dropped and
 * reported. Missing source candles are skipped.
 *
 * <p>Not thread-safe: use one builder per stream, called from one thread.
 */
public final class LiveCandleBuilder {

    /**
     * Result of one update.
     *
     * @param current the latest state of the target candle this update touched, including when the
     *     update closed it; null when the update was late
     * @param closed target candles completed by this update, oldest first
     * @param late true when the update was dropped
     */
    public record Update(Candle current, List<Candle> closed, boolean late) {

        private static final Update LATE = new Update(null, List.of(), true);
    }

    private final Timeframe source;
    private final Timeframe target;

    /** True once seeded or updated; seeding is only allowed before that. */
    private boolean started;

    private long bucketOpen;
    private Candle base;
    private Candle forming;
    private long lateBefore = Long.MIN_VALUE;

    public LiveCandleBuilder(Timeframe source, Timeframe target) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        if (!target.canBuildFrom(source)) {
            throw new IllegalArgumentException("cannot build " + target + " from " + source);
        }
    }

    /**
     * Starts mid-bucket from history: {@code partial} is the target candle built from source
     * candles opening before {@code coveredUntilExclusive}. Live updates for those source candles
     * are then late, so a candle in both history and the stream is not counted twice. Leave the
     * still-forming source candle out of {@code partial}; the stream supplies it.
     *
     * @throws IllegalStateException if called after {@link #update} or a previous seed
     */
    public void seed(Candle partial, long coveredUntilExclusive) {
        Objects.requireNonNull(partial, "partial");
        if (started) {
            throw new IllegalStateException("seed must come before any update");
        }
        long open = partial.openTime();
        if (target.bucketOpen(open) != open) {
            throw new IllegalArgumentException("seed at " + open + " is not aligned to " + target);
        }
        if (coveredUntilExclusive <= open
                || coveredUntilExclusive > target.nextBucketOpen(open)
                || source.bucketOpen(coveredUntilExclusive) != coveredUntilExclusive) {
            throw new IllegalArgumentException(
                    "covered-until "
                            + coveredUntilExclusive
                            + " must be a "
                            + source
                            + " boundary inside the seeded "
                            + target
                            + " bucket");
        }
        started = true;
        bucketOpen = open;
        base = partial;
        lateBefore = coveredUntilExclusive;
    }

    /**
     * Applies the latest snapshot of a source candle.
     *
     * @param sourceClosed true when the exchange marks this source candle final
     * @throws IllegalArgumentException if the candle is not aligned to the source timeframe
     */
    public Update update(Candle sourceCandle, boolean sourceClosed) {
        Objects.requireNonNull(sourceCandle, "sourceCandle");
        long openTime = sourceCandle.openTime();
        if (source.bucketOpen(openTime) != openTime) {
            throw new IllegalArgumentException(
                    "candle at " + openTime + " is not aligned to " + source);
        }
        if (openTime < lateBefore || (forming != null && openTime < forming.openTime())) {
            return Update.LATE;
        }
        started = true;
        List<Candle> closed = new ArrayList<>(2);
        long bucket = target.bucketOpen(openTime);
        if (hasOpenBucket() && bucket != bucketOpen) {
            closed.add(current());
            base = null;
            forming = null;
        }
        if (!hasOpenBucket()) {
            bucketOpen = bucket;
        }
        if (forming != null && openTime > forming.openTime()) {
            base = base == null ? forming : Resampler.merge(base, forming);
        }
        forming = sourceCandle;
        Candle current = current();
        long bucketEnd = target.nextBucketOpen(openTime);
        if (sourceClosed && source.nextBucketOpen(openTime) == bucketEnd) {
            closed.add(current);
            base = null;
            forming = null;
            lateBefore = bucketEnd;
        }
        return new Update(current, List.copyOf(closed), false);
    }

    private boolean hasOpenBucket() {
        return base != null || forming != null;
    }

    private Candle current() {
        Candle merged =
                base == null ? forming : forming == null ? base : Resampler.merge(base, forming);
        return Resampler.withOpenTime(merged, bucketOpen);
    }
}
