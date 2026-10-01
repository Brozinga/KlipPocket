package ru.ytkab0bp.beamklipper.testing

import android.content.SharedPreferences

// In-memory SharedPreferences that, like the real one, throws
// ClassCastException when a value is read as another type than it was stored.
class FakeSharedPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    val data = HashMap<String, Any?>(initial)

    private inline fun <reified T> typed(key: String, default: T): T {
        if (!data.containsKey(key)) return default
        val v = data[key]
        if (v !is T) throw ClassCastException("$key is ${v?.javaClass?.simpleName}")
        return v
    }

    override fun getAll(): MutableMap<String, *> = HashMap(data)
    override fun getString(key: String, defValue: String?): String? {
        if (!data.containsKey(key)) return defValue
        val v = data[key]
        if (v != null && v !is String) throw ClassCastException("$key is ${v.javaClass.simpleName}")
        return v as String?
    }
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = typed(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = typed(key, defValue)
    override fun getFloat(key: String, defValue: Float): Float = typed(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = typed(key, defValue)
    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removed = HashSet<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) data.clear()
            removed.forEach { data.remove(it) }
            data.putAll(pending)
        }
    }

    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}
