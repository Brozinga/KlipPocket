package ru.ytkab0bp.beamklipper.events

import ru.ytkab0bp.eventbus.Event

@Event
data class InstanceCreatedEvent(@JvmField val id: String)
