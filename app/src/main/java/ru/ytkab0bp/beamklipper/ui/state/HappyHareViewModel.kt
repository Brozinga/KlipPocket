package ru.ytkab0bp.beamklipper.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.ytkab0bp.beamklipper.firmwareweb.AndroidFirmwareBackend
import ru.ytkab0bp.beamklipper.firmwareweb.AndroidMmuBackend
import ru.ytkab0bp.beamklipper.hhwizard.HhWizardRules

enum class HhStage { PRINTERS, HOME, TYPE, PAGE, REVIEW, DONE }

/** One line of a wizard page: a question, a menu to open, or a note. */
data class HhItem(
    val id: String,
    val kind: String,
    val title: String,
    val help: String,
    val indent: Int,
    val symbol: String?,
    val value: String?,
    val flag: Boolean,
    val fixed: Boolean,
    val isDefault: Boolean,
    val path: String?,
    val enabled: Boolean,
    val options: List<HhOption>,
)

data class HhOption(val symbol: String, val title: String, val help: String)
data class HhUsb(val label: String, val path: String)

data class HhState(
    val stage: HhStage = HhStage.PRINTERS,
    val busy: Boolean = false,
    val error: String? = null,
    val printers: List<Pair<String, String>> = emptyList(),
    val printerId: String? = null,
    val printerName: String = "",
    val installed: Boolean = false,
    val currentType: String? = null,
    val types: List<HhOption> = emptyList(),
    val selectedType: String? = null,
    val pageTitle: String = "",
    val pageHelp: String = "",
    val items: List<HhItem> = emptyList(),
    val pathStack: List<String> = emptyList(),
    val problems: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val include: Boolean = true,
    val resetState: Boolean = false,
    val includeLines: List<String> = emptyList(),
    val moonrakerLines: List<String> = emptyList(),
    val added: Boolean = false,
    val usb: List<HhUsb> = emptyList(),
)

/** The Happy Hare setup wizard of the app: every screen is a view of what the wizard (hh_wizard.py) answers. */
class HappyHareViewModel(app: Application) : AndroidViewModel(app) {
    private val backend = AndroidMmuBackend(app)
    private val _state = MutableStateFlow(HhState())
    val state: StateFlow<HhState> = _state

    init { loadPrinters() }

    private fun loadPrinters() {
        val list = backend.printers().getJSONArray("printers")
        val printers = (0 until list.length()).map { list.getJSONObject(it).let { p -> p.getString("id") to p.optString("name") } }
        _state.update { it.copy(printers = printers) }
        if (printers.size == 1 && _state.value.printerId == null) selectPrinter(printers[0].first)
    }

    private suspend fun call(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val id = _state.value.printerId ?: return@withContext HhWizardRules.failure("printer")
        backend.call(id, body) ?: HhWizardRules.failure("printer")
    }

    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() } finally { _state.update { it.copy(busy = false) } }
        }
    }

    private fun fail(r: JSONObject) {
        val problems = r.optJSONArray("problems")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        _state.update { it.copy(error = r.optString("error", r.optString("key", "?")), problems = problems) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun selectPrinter(id: String) {
        val name = _state.value.printers.firstOrNull { it.first == id }?.second ?: ""
        _state.update { it.copy(printerId = id, printerName = name) }
        run {
            val r = call(JSONObject().put("op", "open"))
            if (!r.optBoolean("ok")) return@run fail(r)
            _state.update {
                it.copy(
                    stage = HhStage.HOME,
                    installed = r.optBoolean("installed"),
                    currentType = r.optString("type").ifEmpty { null },
                    types = options(r.optJSONArray("types")),
                    selectedType = null,
                )
            }
        }
    }

    /** [edit] reopens the saved answers; otherwise everything starts from the defaults and the MMU is asked first. */
    fun start(edit: Boolean) = run {
        val r = call(JSONObject().put("op", "reset").put("load_saved", edit))
        if (!r.optBoolean("ok")) return@run fail(r)
        val opened = call(JSONObject().put("op", "open"))
        if (opened.optBoolean("ok")) _state.update { it.copy(types = options(opened.optJSONArray("types"))) }
        if (edit) loadPage("", reset = true) else _state.update { it.copy(stage = HhStage.TYPE, selectedType = null, pathStack = emptyList()) }
    }

    fun chooseType(symbol: String) = run {
        val r = call(JSONObject().put("op", "set").put("symbol", symbol).put("value", true))
        if (!r.optBoolean("ok")) return@run fail(r)
        _state.update { it.copy(selectedType = symbol) }
        loadPage("", reset = true)
    }

    private suspend fun loadPage(path: String, reset: Boolean = false, push: Boolean = false) {
        val r = call(JSONObject().put("op", "page").put("path", path))
        if (!r.optBoolean("ok")) return fail(r)
        val items = r.getJSONArray("items")
        val parsed = (0 until items.length()).map { parseItem(items.getJSONObject(it)) }
        _state.update {
            val stack = when {
                reset -> emptyList()
                push -> it.pathStack + path
                else -> it.pathStack
            }
            it.copy(stage = HhStage.PAGE, items = parsed, pageTitle = r.optString("title"), pageHelp = r.optString("help"), pathStack = stack)
        }
    }

    fun openMenu(path: String) = run { loadPage(path, push = true) }

    /** Back inside the questions; false when already at the first page. */
    fun up(): Boolean {
        val s = _state.value
        return when (s.stage) {
            HhStage.PAGE -> if (s.pathStack.isEmpty()) {
                _state.update { it.copy(stage = if (it.selectedType == null && it.installed) HhStage.HOME else HhStage.TYPE) }
                true
            } else {
                val parent = s.pathStack.dropLast(1)
                run { loadPage(parent.lastOrNull() ?: "", reset = true); _state.update { it.copy(pathStack = parent) } }
                true
            }
            HhStage.TYPE, HhStage.REVIEW -> { _state.update { it.copy(stage = if (it.stage == HhStage.REVIEW) HhStage.PAGE else HhStage.HOME) }; true }
            HhStage.HOME -> if (s.printers.size > 1) { _state.update { it.copy(stage = HhStage.PRINTERS) }; true } else false
            HhStage.DONE -> { _state.update { it.copy(stage = HhStage.HOME) }; true }
            HhStage.PRINTERS -> false
        }
    }

    fun setValue(symbol: String, value: Any) = run {
        val r = call(JSONObject().put("op", "set").put("symbol", symbol).put("value", value))
        if (!r.optBoolean("ok")) return@run fail(r)
        loadPage(_state.value.pathStack.lastOrNull() ?: "")
    }

    fun restoreDefault(symbol: String) = run {
        val r = call(JSONObject().put("op", "unset").put("symbol", symbol))
        if (!r.optBoolean("ok")) return@run fail(r)
        loadPage(_state.value.pathStack.lastOrNull() ?: "")
    }

    fun review() = run {
        val r = call(JSONObject().put("op", "review"))
        if (!r.optBoolean("ok")) return@run fail(r)
        _state.update {
            it.copy(stage = HhStage.REVIEW, problems = strings(r.optJSONArray("problems")), warnings = strings(r.optJSONArray("warnings")),
                resetState = it.selectedType != null)
        }
    }

    fun setInclude(on: Boolean) = _state.update { it.copy(include = on) }
    fun setResetState(on: Boolean) = _state.update { it.copy(resetState = on) }

    fun generate() = run {
        val s = _state.value
        val r = call(JSONObject().put("op", "generate").put("include", s.include).put("keep_state", !s.resetState))
        if (!r.optBoolean("ok")) return@run fail(r)
        val opened = call(JSONObject().put("op", "open"))
        _state.update {
            it.copy(stage = HhStage.DONE, installed = true, added = r.optBoolean("added"),
                currentType = opened.optString("type").ifEmpty { it.currentType },
                includeLines = strings(r.optJSONArray("includes")), moonrakerLines = strings(r.optJSONArray("moonraker")))
        }
    }

    fun addNow() = run {
        val r = call(JSONObject().put("op", "add_to_printer"))
        if (!r.optBoolean("ok")) return@run fail(r)
        _state.update { it.copy(added = true) }
    }

    /** The USB devices the phone sees, with the path a printer.cfg [mcu] uses for each. */
    fun loadUsb() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                AndroidFirmwareBackend(getApplication()).devices().mapNotNull { d -> d.serialPath?.let { HhUsb(d.label, it) } }
            }
            _state.update { it.copy(usb = list) }
        }
    }

    private fun strings(a: JSONArray?): List<String> = a?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList()

    private fun options(a: JSONArray?): List<HhOption> = a?.let {
        (0 until it.length()).map { i -> it.getJSONObject(i).let { o -> HhOption(o.getString("symbol"), o.optString("title"), o.optString("help")) } }
    } ?: emptyList()

    private fun parseItem(o: JSONObject): HhItem {
        val kind = o.getString("kind")
        val value = if (o.has("value") && !o.isNull("value")) o.get("value") else null
        return HhItem(
            id = o.getString("id"), kind = kind, title = o.optString("title"), help = o.optString("help"), indent = o.optInt("indent"),
            symbol = o.optString("symbol").ifEmpty { null },
            value = if (kind == "bool") null else value?.toString(),
            flag = kind == "bool" && value == true,
            fixed = o.optBoolean("fixed"), isDefault = o.optBoolean("default", true),
            path = o.optString("path").ifEmpty { null }, enabled = o.optBoolean("enabled", true),
            options = options(o.optJSONArray("options")),
        )
    }
}
