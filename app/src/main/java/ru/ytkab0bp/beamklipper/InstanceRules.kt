package ru.ytkab0bp.beamklipper

import kotlin.math.max

// Rules that decide what a printer instance does, free of Android services so
// they can be unit-tested. KlipperInstance applies them.

// Each running instance gets one of a fixed number of service slots
// (KlippyService_N / MoonrakerService_N).
object SlotAllocator {
    // The slot an instance already holds, else the lowest free one.
    fun allocate(id: String, slotById: Map<String, Int>, slotsCount: Int): Int {
        slotById[id]?.let { return it }
        return when {
            slotById.isEmpty() -> 0
            slotById.size < slotsCount -> {
                val occupied = slotById.values
                (0 until slotsCount).firstOrNull { it !in occupied }
                    ?: throw IllegalStateException("Can't start id=$id: out of slots (slotById=$slotById)")
            }
            else -> throw IllegalStateException("Can't start id=$id: out of slots (slotById.size >= $slotsCount)")
        }
    }
}

// Restarts a Klippy/Moonraker service that died, a few times, and gives up
// when it keeps dying right after starting (a crash loop).
object WatchdogPolicy {
    const val MAX_RETRIES = 3
    const val MIN_RUN_MS = 8000L
    const val RESTART_DELAY_MS = 1500L

    data class Decision(val attempt: Int, val wasShort: Boolean, val shouldRestart: Boolean)

    // [previousAttempts] restarts already made; [lastRunningMs] is when the
    // instance last reached RUNNING.
    fun decide(previousAttempts: Int, lastRunningMs: Long, nowMs: Long): Decision {
        val wasShort = (nowMs - lastRunningMs) < MIN_RUN_MS && previousAttempts > 0
        val attempt = previousAttempts + 1
        return Decision(attempt, wasShort, shouldRestart = attempt <= MAX_RETRIES && !wasShort)
    }

    // Backs off a little more with every attempt.
    fun restartDelayMs(attempt: Int): Long = max(500L + attempt * 1200L, RESTART_DELAY_MS)

    // A restart that reconnects earns one attempt back.
    fun attemptsAfterReconnect(attempts: Int): Int = max(0, attempts - 1)
}

// What the Start/Stop buttons do for an instance in a given state.
object InstanceActions {
    fun isActive(state: KlipperInstance.State): Boolean =
        state == KlipperInstance.State.RUNNING || state == KlipperInstance.State.STARTING

    // A stopped instance can only start while a slot is free; one that is
    // still stopping can be restarted (it already holds its slot).
    fun canStart(state: KlipperInstance.State, hasFreeSlots: Boolean): Boolean = when (state) {
        KlipperInstance.State.IDLE -> hasFreeSlots
        KlipperInstance.State.STOPPING -> true
        else -> false
    }
}

// The instance list is only republished when its instances (by id, or by name
// before they have one) or their order change.
object InstanceLists {
    fun sameInstances(a: List<KlipperInstance>, b: List<KlipperInstance>): Boolean =
        a.size == b.size && a.zip(b).all { (x, y) -> (x.id ?: x.name) == (y.id ?: y.name) }
}

// Creating a printer profile from the "new profile" sheet.
object NewInstance {
    // Every profile needs a slot, and a starter printer.cfg must be chosen.
    fun canCreate(existingProfiles: Int, slotsCount: Int, configFile: String?): Boolean =
        existingProfiles < slotsCount && !configFile.isNullOrEmpty()
}

// The entries of the in-app log picker for one instance.
object LogSources {
    data class Entry(val id: String, val label: String, val fileName: String)

    fun forInstance(instanceId: String?, name: String): List<Entry> {
        val display = name.ifBlank { instanceId ?: "?" }
        return listOf(
            Entry("klippy_$instanceId", "Klipper · $display", "klippy.log"),
            Entry("moonraker_$instanceId", "Moonraker · $display", "moonraker.log"),
            Entry("octoeverywhere_$instanceId", "OctoEverywhere · $display", "octoeverywhere.log"),
            Entry("obico_$instanceId", "Obico · $display", "obico.log")
        )
    }
}
