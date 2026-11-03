package org.eventbus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class Delivery {
    @SuppressWarnings("unchecked")
    static void deliver(Object event,
                        Map<Class<?>, ? extends List<Consumer<?>>> subscribers,
                        Map<Class<?>, ? extends List<Filtered<?>>> filtered) {
        Class<?> clazz = event.getClass();
        while (clazz != null) {
            deliverExact(clazz, event, subscribers, filtered);
            clazz = clazz.getSuperclass();
        }
    }

    @SuppressWarnings("unchecked")
    private static void deliverExact(Class<?> clazz,
                                     Object event,
                                     Map<Class<?>, ? extends List<Consumer<?>>> subscribers,
                                     Map<Class<?>, ? extends List<Filtered<?>>> filtered) {
        List<Consumer<?>> subs = subscribers.get(clazz);
        if (subs != null) {
            for (Consumer<?> c : subs) {
                try {
                    ((Consumer<Object>) c).accept(event);
                } catch (Exception e) {
                    handleSubscriberException(e);
                }
            }
        }
        List<Filtered<?>> fs = filtered.get(clazz);
        if (fs != null) {
            for (Filtered<?> f : fs) {
                try {
                    if (((Predicate<Object>) f.filter).test(event)) {
                        ((Consumer<Object>) f.consumer).accept(event);
                    }
                } catch (Exception e) {
                    handleSubscriberException(e);
                }
            }
        }
    }

    private static void handleSubscriberException(Exception e) {
        Thread.currentThread().getUncaughtExceptionHandler()
                .uncaughtException(Thread.currentThread(), e);
    }

    static final class Filtered<T> {
        final Predicate<T> filter;
        final Consumer<T> consumer;

        Filtered(Predicate<T> filter, Consumer<T> consumer) {
            this.filter = Objects.requireNonNull(filter);
            this.consumer = Objects.requireNonNull(consumer);
        }
    }
}
