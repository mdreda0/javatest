package org.throttler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimThrottler implements Throttler {

    private final int  maxPermits;
    private final long periodNanos;
    private final AtomicInteger permits;
    private final CopyOnWriteArrayList<Runnable> waiters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    private volatile long nextRefilNanos;

    public RateLimThrottler(int maxPermits, Duration period) {
        this(maxPermits, period, Executors.newSingleThreadScheduledExecutor( r -> {
            Thread t = new Thread(r, "throttler-scheduler");
            t.setDaemon(true);
            return t;
        }));
    }

    public RateLimThrottler(int maxPermits, Duration period, ScheduledExecutorService scheduler) {
        if (maxPermits <= 0) {
            throw new IllegalArgumentException("maxPermits must be > 0");
        }
        this.maxPermits = maxPermits;
        this.periodNanos = period.toNanos();
        if (this.periodNanos <= 0 ) {
            throw new IllegalArgumentException("period must be > 0");
        }
        this.permits = new AtomicInteger(maxPermits);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.nextRefilNanos = System.nanoTime() + periodNanos;
        scheduleRefill();
    }
    @Override
    public ThrottleResult shouldProceed() {
        refillIfDue();
        int current;
        do {
            current = permits.get();
            if (current <= 0) return ThrottleResult.DO_NOT_PROCEED;
        } while (!permits.compareAndSet(current, current - 1));

        return ThrottleResult.PROCEED;
    }

    @Override
    public void notifyWhenCanProceed(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        waiters.add(callback);

        if(permits.get() > 0) {
            scheduler.execute(this::drainWaiters);
        }
    }

    private void refillIfDue() {
        long now = System.nanoTime();
        if(now >= nextRefilNanos) {
            permits.set(maxPermits);
            nextRefilNanos = now + periodNanos;
            drainWaiters();
        }
    }

    private void scheduleRefill() {
        scheduler.scheduleAtFixedRate(this::refillIfDue, periodNanos, periodNanos, TimeUnit.NANOSECONDS);
    }

    private void drainWaiters() {
        if (waiters.isEmpty()) return;
        Runnable[] pending = waiters.toArray(waiters.toArray(new Runnable[0]));
        waiters.clear();

        for (Runnable r : pending) {
            try {
                r.run();
            } catch (Exception e) {
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), e);
            }
        }
    }
    public void shutdown() {
        scheduler.shutdown();
    }
}
