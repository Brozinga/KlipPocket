package ru.ytkab0bp.eventbus.demo.events;

import ru.ytkab0bp.eventbus.Event;

@Event
public class TestEvent {
    public long startMs = System.currentTimeMillis();
}
