package org.eventbus;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MultiThreadedEventBus implements CoalescingEventBus {

    private final ExecutorService dispatcher;
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> subscribers = new ConcurrentHashMap<>();
    private final Map<Class<?>, CopyOnWriteArrayList<Delivery.Filtered<?>>> filtered = new ConcurrentHashMap<>();

    private final Set<Class<?>> coalescingTypes = ConcurrentHashMap.newKeySet();
    private final Map<Class<?>, CoalescedSlot> slots = new ConcurrentHashMap<>();

    public MultiThreadedEventBus(ExecutorService dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher);
    }

    public MultiThreadedEventBus() {
        this(Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread thread = new Thread(r, "event-bus-dispatch");
                    thread.setDaemon(true);
                    return thread;
                }
        ));
    }

    @Override
    public void publishEvent(Object event) {
        Objects.requireNonNull(event, "event");
        Class<?> clazz = event.getClass();

        if (coalescingTypes.contains(clazz)) {
            CoalescedSlot slot = slots.computeIfAbsent(clazz, k -> new CoalescedSlot());
            slot.latest.set(event);
            if (slot.scheduled.compareAndSet(false, true)) {
                dispatcher.execute(() -> processCoalesced(slot));
            }
            return;
        }

        dispatcher.execute(() -> Delivery.deliver(event, subscribers, filtered));
    }

    @Override
    public <T> void addSubscriber(Class<T> clazz, Consumer<T> subscriber) {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(subscriber, "subscriber");
        subscribers.computeIfAbsent(clazz, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    @Override
    public <T> void addSubscriberForFilteredEvents(Class<T> clazz,
                                                   Predicate<T> filter,
                                                   Consumer<T> subscriber) {
        Objects.requireNonNull(clazz, "clazz");
        filtered.computeIfAbsent(clazz, k -> new CopyOnWriteArrayList<>())
                .add(new Delivery.Filtered<>(filter, subscriber));
    }

    @Override
    public <T> void addCoalescingSubscriber(Class<T> clazz, Consumer<T> subscriber) {
        Objects.requireNonNull(clazz, "clazz");
        coalescingTypes.add(clazz);
        addSubscriber(clazz, subscriber);
    }

    public void shutdown() {
        dispatcher.shutdown();
    }

    private void processCoalesced(CoalescedSlot slot) {
        Object event;
        while ((event = slot.latest.getAndSet(null)) != null) {
            Delivery.deliver(event, subscribers, filtered);
        }
        slot.scheduled.set(false);
        if (slot.latest.get() != null && slot.scheduled.compareAndSet(false, true)) {
            dispatcher.execute(() -> processCoalesced(slot));
        }
    }

    private static final class CoalescedSlot {
        final AtomicReference<Object> latest = new AtomicReference<>();
        final AtomicBoolean scheduled = new AtomicBoolean(false);
    }
}