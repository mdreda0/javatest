package org.eventbus;

public interface CoalescingEventBus extends EventBus {
    <T> void addCoalescingSubscriber(Class<T> clazz, java.util.function.Consumer<T> subscriber);
}
