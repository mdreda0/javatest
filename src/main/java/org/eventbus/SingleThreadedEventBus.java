package org.eventbus;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SingleThreadedEventBus implements EventBus {
    private final Map<Class<?>, List<Consumer<?>>> subscribers = new HashMap<>();
    private final Map<Class<?>, List<Delivery.Filtered<?>>> filtered = new HashMap<>();

    @Override
    public void publishEvent(Object event) {
        Objects.requireNonNull(event, "event");
        Delivery.deliver(event, subscribers, filtered);
    }

    @Override
    public <T> void addSubscriber(Class<T> clazz, Consumer<T> subscriber) {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(subscriber, "subscriber");
        subscribers.computeIfAbsent(clazz, k -> new ArrayList<>()).add(subscriber);
    }

    @Override
    public <T> void addSubscriberForFilteredEvents(Class<T> clazz,
                                                   Predicate<T> filter,
                                                   Consumer<T> subscriber) {
        Objects.requireNonNull(clazz, "clazz");
        filtered.computeIfAbsent(clazz, k -> new ArrayList<>())
                .add(new Delivery.Filtered<>(filter, subscriber));
    }

}
