package org.slidingwindow;

package com.example.stats;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class SimpleSlidingWindowStatistics implements SlidingWindowStatistics {

    private final int windowSize;
    private final Deque<Integer> window = new ArrayDeque<>();
    private final TreeMap<Integer, Integer> frequencies = new TreeMap<>();
    private long sum = 0;

    private final Object lock = new Object(); // protects window + frequencies + sum

    private final AtomicReference<Statistics> latest = new AtomicReference<>(EmptyStatistics.INSTANCE);

    private final Executor callbackExecutor;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public SimpleSlidingWindowStatistics(int windowSize) {
        this(windowSize, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "stats-callback");
            t.setDaemon(true);
            return t;
        }));
    }

    public SimpleSlidingWindowStatistics(int windowSize, Executor callbackExecutor) {
        if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be > 0");
        this.windowSize = windowSize;
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor);
    }

    @Override
    public void add(int measurement) {
        Statistics snapshot;
        synchronized (lock) {
            window.addLast(measurement);
            sum += measurement;
            frequencies.merge(measurement, 1, Integer::sum);

            if (window.size() > windowSize) {
                int evicted = window.removeFirst();
                sum -= evicted;
                frequencies.computeIfPresent(evicted, (k, v) -> v == 1 ? null : v - 1);
            }

            snapshot = buildSnapshot();
            latest.set(snapshot);
        }

        // notify outside the lock
        notifySubscribers(snapshot);
    }

    @Override
    public void subscribeForStatistics(StatisticsListener listener, Predicate<Statistics> filter) {
        Objects.requireNonNull(listener);
        subscriptions.add(new Subscription(listener, filter));
        // optionally deliver current snapshot immediately
        Statistics current = latest.get();
        if (filter == null || filter.test(current)) {
            callbackExecutor.execute(() -> listener.onStatistics(current));
        }
    }

    @Override
    public void unsubscribe(StatisticsListener listener) {
        subscriptions.removeIf(s -> s.listener == listener);
    }

    @Override
    public Statistics getLatestStatistics() {
        return latest.get();
    }

    private void notifySubscribers(Statistics snapshot) {
        for (Subscription sub : subscriptions) {
            if (sub.filter == null || sub.filter.test(snapshot)) {
                callbackExecutor.execute(() -> {
                    try {
                        sub.listener.onStatistics(snapshot);
                    } catch (Exception ignored) {
                        // production: log, metric, dead-letter, etc.
                    }
                });
            }
        }
    }

    private Statistics buildSnapshot() {
        if (window.isEmpty()) return EmptyStatistics.INSTANCE;
        return new Snapshot(sum, window.size(), frequencies);
    }

    // ----- internal types -----

    private static final class Subscription {
        final StatisticsListener listener;
        final Predicate<Statistics> filter;
        Subscription(StatisticsListener l, Predicate<Statistics> f) {
            this.listener = l;
            this.filter = f;
        }
    }

    private static final class Snapshot implements Statistics {
        private final double mean;
        private final int mode;
        private final int count;
        private final int min;
        private final int max;
        private final TreeMap<Integer, Integer> freqCopy; // or pre-compute percentiles if desired

        Snapshot(long sum, int count, TreeMap<Integer, Integer> frequencies) {
            this.count = count;
            this.mean = count == 0 ? 0.0 : (double) sum / count;
            this.min = frequencies.firstKey();
            this.max = frequencies.lastKey();

            // mode
            int bestVal = 0, bestFreq = -1;
            for (var e : frequencies.entrySet()) {
                if (e.getValue() > bestFreq || (e.getValue() == bestFreq && e.getKey() < bestVal)) {
                    bestFreq = e.getValue();
                    bestVal = e.getKey();
                }
            }
            this.mode = bestVal;
            this.freqCopy = new TreeMap<>(frequencies); // defensive
        }

        @Override public double getMean() { return mean; }
        @Override public int getMode() { return mode; }
        @Override public int getCount() { return count; }
        @Override public int getMin() { return min; }
        @Override public int getMax() { return max; }

        @Override
        public int getPercentile(int pctile) {
            if (pctile < 0 || pctile > 100) throw new IllegalArgumentException("pctile 0-100");
            if (count == 0) return 0;
            // nearest-rank
            int rank = (int) Math.ceil(pctile / 100.0 * count);
            if (rank < 1) rank = 1;
            int seen = 0;
            for (var e : freqCopy.entrySet()) {
                seen += e.getValue();
                if (seen >= rank) return e.getKey();
            }
            return freqCopy.lastKey();
        }
    }

    private enum EmptyStatistics implements Statistics {
        INSTANCE;
        public double getMean() { return 0; }
        public int getMode() { return 0; }
        public int getPercentile(int p) { return 0; }
        public int getCount() { return 0; }
        public int getMin() { return 0; }
        public int getMax() { return 0; }
    }
}
