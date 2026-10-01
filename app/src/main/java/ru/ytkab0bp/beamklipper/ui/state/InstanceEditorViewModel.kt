package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ytkab0bp.beamklipper.InstanceIcon
import ru.ytkab0bp.beamklipper.NewInstance
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class InstanceEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val _editingInstance = MutableStateFlow<KlipperInstance?>(null)
    val editingInstance: StateFlow<KlipperInstance?> = _editingInstance.asStateFlow()

    private val _filesList = MutableStateFlow<List<String>>(emptyList())
    val filesList: StateFlow<List<String>> = _filesList.asStateFlow()

    private val _configFile = MutableStateFlow<String?>(null)
    val configFile: StateFlow<String?> = _configFile.asStateFlow()

    private val _defaultName = MutableStateFlow<String?>(null)
    val defaultName: StateFlow<String?> = _defaultName.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private fun defaultNameTemplate(): (Int) -> String {
        val ctx = getApplication<Application>()
        return { n -> ctx.getString(R.string.InstanceDefaultName, n) }
    }

    private fun computeDefaultName(): String {
        val (dbNames, slotNames) = collectExistingNames()
        return InstanceNames.defaultName(defaultNameTemplate(), (dbNames + slotNames).distinct())
    }

    private fun collectExistingNames(): Pair<List<String>, List<String>> {
        val dbNames = runCatching {
            KlipperApp.getDatabaseOrNull()?.getInstances()?.map { it.name }.orEmpty()
        }.getOrDefault(emptyList())
        val slotNames = runCatching {
            KlipperInstance.getSlotInstancesNames()
        }.getOrDefault(emptyList())
        return dbNames to slotNames
    }

    // Names other profiles use: the one being edited may keep its own name.
    private fun takenNames(editing: KlipperInstance?): List<String> {
        val (dbNames, slotNames) = collectExistingNames()
        val editingId = editing?.id ?: return (dbNames + slotNames).distinct()
        val dbInstances = runCatching {
            KlipperApp.getDatabaseOrNull()?.getInstances().orEmpty()
        }.getOrDefault(emptyList())
        return (dbNames + slotNames).filter { name ->
            dbInstances.firstOrNull { it.name == name }?.id != editingId
        }.distinct()
    }

    private fun ensureUniqueName(desired: String, editing: KlipperInstance?): String =
        InstanceNames.unique(desired, takenNames(editing)) { computeDefaultName() }

    fun loadForCreate() {
        _editingInstance.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                KlipperApp.bundleInstallJob.await()
            } catch (_: Throwable) {}
            _defaultName.value = computeDefaultName()
            val files = runCatching {
                File(KlipperApp.INSTANCE.filesDir, "klipper/config").listFiles()?.map { it.name }?.sorted()
            }.getOrNull() ?: emptyList()
            _filesList.value = files
            _configFile.value = InstanceNames.defaultConfig(files)
        }
    }

    fun loadForEdit(instance: KlipperInstance) {
        _editingInstance.value = instance
        _defaultName.value = instance.name
        viewModelScope.launch(Dispatchers.IO) {
            try {
                KlipperApp.bundleInstallJob.await()
            } catch (_: Throwable) {}
            val files = runCatching {
                File(KlipperApp.INSTANCE.filesDir, "klipper/config").listFiles()?.map { it.name }?.sorted()
            }.getOrNull() ?: emptyList()
            _filesList.value = files
            _configFile.value = InstanceNames.defaultConfig(files)
        }
    }

    fun selectConfig(file: String) {
        _configFile.value = file
    }

    fun save(name: String, autostart: Boolean, onDone: () -> Unit) {
        val editing = _editingInstance.value
        val finalizedName = run {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                ensureUniqueName(trimmed, editing)
            } else {
                computeDefaultName()
            }
        }
        if (editing != null) {
            editing.name = finalizedName
            editing.autostart = autostart
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    KlipperApp.bundleInstallJob.await()
                } catch (_: Throwable) {}
                KlipperApp.DATABASE.update(editing)
                onDone()
            }
            return
        }

        val existing = runCatching { KlipperApp.DATABASE.getInstances().size }.getOrDefault(0)
        if (!NewInstance.canCreate(existing, KlipperInstance.SLOTS_COUNT, _configFile.value)) {
            onDone()
            return
        }

        val inst = KlipperInstance().apply {
            id = UUID.randomUUID().toString()
            this.name = finalizedName
            this.autostart = autostart
            this.icon = InstanceIcon.PRINTER
        }
        val cfg = File(inst.publicDirectory, "config/printer.cfg")
        val cfgText = _configFile.value!!
        _saving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                try {
                    KlipperApp.bundleInstallJob.await()
                } catch (_: Throwable) {}
                cfg.parentFile?.mkdirs()
                try {
                    FileInputStream(File(KlipperApp.INSTANCE.filesDir, "klipper/config/$cfgText")).use { fis ->
                        FileOutputStream(cfg).use { fos -> fis.copyTo(fos) }
                    }
                } catch (e: Exception) {
                    Log.w("InstanceEditor", "Failed to copy config file", e)
                }
                KlipperApp.DATABASE.insert(inst)
            } finally {
                _saving.value = false
                onDone()
            }
        }
    }
}
