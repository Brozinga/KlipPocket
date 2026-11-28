package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.InstanceActions
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.service.web.WebRouting
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.collections.plus

data class TemperatureInfo(
    val current: Double = 0.0,
    val target: Double = 0.0
)

data class PrinterTemperatures(
    val extruder: TemperatureInfo? = null,
    val bed: TemperatureInfo? = null
)

class PrintMonitorViewModel(app: Application): AndroidViewModel(app) {
    val instances: StateFlow<List<KlipperInstance>> = AppState.instances
    val instanceStates: StateFlow<Map<String, KlipperInstance.State>> = AppState.instanceStates
    val webState: StateFlow<KlipperInstance.State> = AppState.webState
    val webFrontend: StateFlow<String> = AppState.webFrontend
    val cameraEnabled: StateFlow<Boolean> = AppState.cameraEnabled
    val selectedInstanceId = mutableStateOf<String?>(null)

    fun selectInstance(id: String?) {
        selectedInstanceId.value = id
    }


    private val _temperatures = MutableStateFlow<Map<String, PrinterTemperatures>>(emptyMap())
    val temperatures: StateFlow<Map<String, PrinterTemperatures>> = _temperatures.asStateFlow()

    val anyRunning: Boolean
        get() = instances.value.any { inst ->
            val state = instanceStates.value[inst.id ?: inst.name] ?: inst.getState()
            InstanceActions.isActive(state)
        }

    val activeInstances: List<KlipperInstance>
        get() = instances.value.filter { inst ->
            val state = instanceStates.value[inst.id ?: inst.name] ?: inst.getState()
            InstanceActions.isActive(state)
        }

    /**
     * Start bucle
     */
    fun startTemperaturePolling(){
        viewModelScope.launch(Dispatchers.IO){
            while (isActive) {
                val currentId = selectedInstanceId.value
                if (currentId != null) {
                    val instance = activeInstances.find { (it.id ?: it.name) == currentId }
                    if (instance != null) {
                        val temps = fetchInstanceTemperatures(instance)
                        _temperatures.update { currentMap ->
                            currentMap + (currentId to temps)
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    private fun fetchInstanceTemperatures(instance: KlipperInstance): PrinterTemperatures {
        val cfg = File(File(instance.publicDirectory, "config"), "moonraker.conf")
        val port = try {
            if (cfg.exists()) WebRouting.moonrakerPortFromConfig(cfg.readText()) else null
        } catch (_: Throwable) { null } ?: WebRouting.DEFAULT_MOONRAKER_PORT

        return try {
            val url = URL("http://127.0.0.1:$port/printer/objects/query?extruder&heater_bed")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1000
                readTimeout = 1500
            }
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(responseText)
            val status = json.optJSONObject("result")?.optJSONObject("status") ?: return PrinterTemperatures()

            val extruderObj = status.optJSONObject("extruder")
            val bedObj = status.optJSONObject("heater_bed")

            PrinterTemperatures(
                extruder = extruderObj?.let {
                    TemperatureInfo(
                        current = it.optDouble("temperature", 0.0),
                        target = it.optDouble("target", 0.0)
                    )
                },
                bed = bedObj?.let {
                    TemperatureInfo(
                        current = it.optDouble("temperature", 0.0),
                        target = it.optDouble("target", 0.0)
                    )
                }
            )
        } catch (_: Exception) {
            PrinterTemperatures()
        }
    }
}