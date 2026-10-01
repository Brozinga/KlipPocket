package ru.ytkab0bp.eventbus;

public interface EventBusCompatConsumer {
    /**
     * @param obj Listener
     * @param event Event
     */
    void onEvent(Object obj, Object event);
}
