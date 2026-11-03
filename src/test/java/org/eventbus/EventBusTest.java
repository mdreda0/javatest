package org.eventbus;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class EventBusTest {
    static class PriceUpdate {
        final double price;
        PriceUpdate(double price) { this.price = price; }
    }

    @Test
    void singleThreaded_deliversOnCallingThread() {
        EventBus bus = new SingleThreadedEventBus();
        Thread calling = Thread.currentThread();
        AtomicInteger count = new AtomicInteger();

        bus.addSubscriber(PriceUpdate.class, e -> {
            assertSame(calling, Thread.currentThread());
            count.incrementAndGet();
        });

        bus.publishEvent(new PriceUpdate(1.0));
        assertEquals(1, count.get());
    }

    @Test
    void multiThreaded_coalescesToLatest() throws Exception {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        MultiThreadedEventBus bus = new MultiThreadedEventBus(exec);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger();

        bus.addCoalescingSubscriber(PriceUpdate.class, e -> {
            received.incrementAndGet();
            if (e.price == 3.0) latch.countDown();
        });

        bus.publishEvent(new PriceUpdate(1.0));
        bus.publishEvent(new PriceUpdate(2.0));
        bus.publishEvent(new PriceUpdate(3.0));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, received.get());          // only the latest was delivered

        bus.shutdown();
        exec.shutdown();
    }
}
