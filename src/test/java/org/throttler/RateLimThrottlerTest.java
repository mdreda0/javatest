package org.throttler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RateLimThrottlerTest {

    private RateLimThrottler throttler;

    @AfterEach
    void tearDown() {
        if (throttler != null) {
            throttler.shutdown();
        }
    }

    @Test
    void shouldProceed_allowsUpToMaxPermits() {
        throttler = new RateLimThrottler(3, Duration.ofSeconds(1));

        assertEquals(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());
    }

    @Test
    void shouldProceed_rejectsWhenExhausted() {
        throttler = new RateLimThrottler(1, Duration.ofMillis(500));

        assertEquals(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());
    }

    @Test
    void permitsRefillAfterPeriod() throws Exception {
        throttler = new RateLimThrottler(2, Duration.ofMillis(200));

        assertEquals(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());

        Thread.sleep(250); // wait for refill

        assertEquals(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());
    }

    @Test
    void notifyWhenCanProceed_isCalledAfterRefill() throws Exception {
        throttler = new RateLimThrottler(1, Duration.ofMillis(150));

        // exhaust the permit
        assertEquals(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed());
        assertEquals(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        throttler.notifyWhenCanProceed(() -> {
            calls.incrementAndGet();
            latch.countDown();
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS), "callback was not invoked");
        assertEquals(1, calls.get());
    }

    @Test
    void notifyWhenCanProceed_calledImmediatelyWhenPermitsAvailable() throws Exception {
        throttler = new RateLimThrottler(5, Duration.ofSeconds(1));

        CountDownLatch latch = new CountDownLatch(1);
        throttler.notifyWhenCanProceed(latch::countDown);

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS),
                "callback should fire immediately when permits exist");
    }

    @Test
    void multipleWaiters_areAllNotified() throws Exception {
        throttler = new RateLimThrottler(1, Duration.ofMillis(100));

        // exhaust
        throttler.shouldProceed();

        int waiterCount = 5;
        CountDownLatch latch = new CountDownLatch(waiterCount);
        List<Integer> order = new ArrayList<>();

        for (int i = 0; i < waiterCount; i++) {
            final int id = i;
            throttler.notifyWhenCanProceed(() -> {
                order.add(id);
                latch.countDown();
            });
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(waiterCount, order.size());
    }

    @Test
    void callbackException_doesNotPreventOtherCallbacks() throws Exception {
        throttler = new RateLimThrottler(1, Duration.ofMillis(100));
        throttler.shouldProceed(); // exhaust

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger goodCalls = new AtomicInteger();

        throttler.notifyWhenCanProceed(() -> {
            throw new RuntimeException("boom");
        });
        throttler.notifyWhenCanProceed(() -> {
            goodCalls.incrementAndGet();
            latch.countDown();
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(1, goodCalls.get());
    }

    @Test
    void invalidConstruction_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimThrottler(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimThrottler(-1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimThrottler(1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimThrottler(1, Duration.ofMillis(-10)));
    }

    @Test
    void concurrentShouldProceed_neverExceedsLimit() throws Exception {
        int maxPermits = 10;
        throttler = new RateLimThrottler(maxPermits, Duration.ofSeconds(2));

        int threads = 20;
        int attemptsPerThread = 100;
        AtomicInteger proceeds = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < attemptsPerThread; j++) {
                        if (throttler.shouldProceed() == Throttler.ThrottleResult.PROCEED) {
                            proceeds.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));

        // In a single window we must not have granted more than maxPermits
        assertTrue(proceeds.get() <= maxPermits,
                "granted " + proceeds.get() + " but max is " + maxPermits);
    }
}