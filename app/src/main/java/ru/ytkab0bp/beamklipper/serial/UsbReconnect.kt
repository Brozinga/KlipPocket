package ru.ytkab0bp.beamklipper.serial

// When a printer is power cycled with the cable plugged in, the board can show up on USB
// before it is ready to be opened, so opening the port is retried a few times.
object UsbReconnect {
    // Let the board settle after USB_DEVICE_ATTACHED before opening it.
    const val ATTACH_SETTLE_MS = 300L

    private val RETRY_DELAYS_MS = longArrayOf(500, 1000, 2000, 3000, 4000)

    // Delay before retry number [attempt] (0-based), or null when it is time to give up.
    fun retryDelay(attempt: Int): Long? = RETRY_DELAYS_MS.getOrNull(attempt)
}

// One slot per port name. A late event about an old connection must not tear down the
// new one that took its place, so removal can be limited to the value the caller holds.
class PortSlots<T : Any> {
    private val slots = HashMap<String, T>()

    @Synchronized
    fun get(uid: String): T? = slots[uid]

    @Synchronized
    fun keys(): List<String> = slots.keys.toList()

    // Stores [value] and returns the one it replaced, if any.
    @Synchronized
    fun put(uid: String, value: T): T? = slots.put(uid, value)

    @Synchronized
    fun remove(uid: String): T? = slots.remove(uid)

    // Removes the slot only when it still holds [value].
    @Synchronized
    fun removeIfCurrent(uid: String, value: T): Boolean {
        if (slots[uid] !== value) return false
        slots.remove(uid)
        return true
    }
}
