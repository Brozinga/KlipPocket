package ru.ytkab0bp.beamklipper.events

import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.eventbus.Event

@Event
data class InstanceStateChangedEvent(@JvmField val id: String, @JvmField val state: KlipperInstance.State)
