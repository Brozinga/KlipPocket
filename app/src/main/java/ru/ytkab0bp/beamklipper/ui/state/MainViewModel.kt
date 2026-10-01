package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.InstanceActions
import ru.ytkab0bp.beamklipper.KlipperInstance

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val instances: StateFlow<List<KlipperInstance>> = AppState.instances
    val instanceStates: StateFlow<Map<String, KlipperInstance.State>> = AppState.instanceStates
    val webState: StateFlow<KlipperInstance.State> = AppState.webState
    val webFrontend: StateFlow<String> = AppState.webFrontend

    val anyRunning: Boolean
        get() = instances.value.any { InstanceActions.isActive(it.getState()) }

    fun toggle(instance: KlipperInstance) {
        val id = instance.id ?: return
        val canonical = KlipperInstance.getInstance(id) ?: return
        val state = canonical.getState()
        if (InstanceActions.isActive(state)) {
            canonical.stop()
            if (canonical.autostart) {
                canonical.autostart = false
                KlipperApp.DATABASE.update(canonical)
            }
        } else if (InstanceActions.canStart(state, KlipperInstance.hasFreeSlots())) {
            canonical.start()
        }
    }

    fun runStopAll() {
        val instances = instances.value.mapNotNull { inst ->
            inst.id?.let { id -> KlipperInstance.getInstance(id) }
        }
        if (instances.isEmpty()) return
        if (instances.any { InstanceActions.isActive(it.getState()) }) {
            for (inst in instances) {
                if (InstanceActions.isActive(inst.getState())) {
                    inst.stop()
                    if (inst.autostart) {
                        inst.autostart = false
                        KlipperApp.DATABASE.update(inst)
                    }
                }
            }
        } else {
            for (inst in instances) {
                val state = inst.getState()
                if (state == KlipperInstance.State.IDLE || state == KlipperInstance.State.STOPPING) {
                    // No free slot for the rest either: stop trying.
                    if (!InstanceActions.canStart(state, KlipperInstance.hasFreeSlots())) return
                    inst.start()
                }
            }
        }
    }

    fun delete(instance: KlipperInstance) {
        val id = instance.id ?: return
        val canonical = KlipperInstance.getInstance(id) ?: return
        canonical.stop()
        viewModelScope.launch(Dispatchers.IO) {
            KlipperApp.DATABASE.delete(canonical)
        }
    }
}
