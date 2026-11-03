package org.slidingwindow;
import java.util.function.Predicate;

public interface SlidingWindowStatistics {

    /**
     * Adds a measurement to the sliding window.
     * Thread-safe; may be called from any thread.
     */
    void add(int measurement);

    /**
     * Registers a listener. Callbacks are delivered on a single background thread
     * (or the Executor supplied at construction) so the listener does not need to be thread-safe.
     *
     * @param listener  receives a fresh Statistics snapshot
     * @param filter    only notified when the predicate returns true (may be null = always notify)
     */
    void subscribeForStatistics(StatisticsListener listener, Predicate<Statistics> filter);

    /**
     * Convenience overload – notifies on every update.
     */
    default void subscribeForStatistics(StatisticsListener listener) {
        subscribeForStatistics(listener, null);
    }

    /**
     * Returns an immutable snapshot of the current window statistics.
     * Thread-safe.
     */
    Statistics getLatestStatistics();

    void unsubscribe(StatisticsListener listener);

    // ----- nested types -----

    interface Statistics {
        double getMean();
        int getMode();                  // value with highest frequency; lowest value wins ties
        int getPercentile(int pctile);  // 0 ≤ pctile ≤ 100; nearest-rank method
        int getCount();
        // useful extras
        int getMin();
        int getMax();
    }

    @FunctionalInterface
    interface StatisticsListener {
        void onStatistics(Statistics stats);
    }
}