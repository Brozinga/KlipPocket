package ru.ytkab0bp.eventbus;

/**
 * @hide
 */
public interface EventBusListenerImpl {
    /**
     * Called to deliver events to the listener
     * @param obj Listener object
     * @param event Event object
     */
    void onEvent(Object obj, Object event);

    /**
     * @param clz Event to check
     * @return If we can fire parent event
     */
    default boolean canFireParent(Class<?> clz) {
        return false;
    }
}
