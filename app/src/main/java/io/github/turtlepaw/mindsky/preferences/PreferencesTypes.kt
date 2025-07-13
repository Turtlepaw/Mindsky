package io.github.turtlepaw.mindsky.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Base preference with caching
abstract class Preference<T>(
    val key: String,
    val defaultValue: T
) {
    private var _cachedValue: T? = null
    private val _stateFlow = MutableStateFlow(defaultValue)
    val stateFlow: StateFlow<T> = _stateFlow.asStateFlow()

    abstract fun read(prefs: SharedPreferences): T
    abstract fun write(prefs: SharedPreferences, value: T)

    fun getValue(prefs: SharedPreferences): T {
        if (_cachedValue == null) {
            _cachedValue = read(prefs)
            _stateFlow.value = _cachedValue!!
        }
        return _cachedValue!!
    }

    fun setValue(prefs: SharedPreferences, value: T) {
        write(prefs, value)
        _cachedValue = value
        _stateFlow.value = value
    }

    // For external updates (from your UI lib)
    fun invalidateCache(prefs: SharedPreferences) {
        _cachedValue = null
        _stateFlow.value = getValue(prefs)
    }
}

// Concrete preference types
class BooleanPreference(
    key: String,
    defaultValue: Boolean
) : Preference<Boolean>(key, defaultValue) {
    override fun read(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun write(prefs: SharedPreferences, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }
}

class EnumPreference<T : Enum<T>>(
    key: String,
    defaultValue: T,
    private val enumClass: Class<T>
) : Preference<T>(key, defaultValue) {
    override fun read(prefs: SharedPreferences): T {
        val ordinal = prefs.getInt(key, defaultValue.ordinal)
        return enumClass.enumConstants?.getOrNull(ordinal) ?: defaultValue
    }

    override fun write(prefs: SharedPreferences, value: T) {
        prefs.edit { putInt(key, value.ordinal) }
    }
}

class StringPreference(
    key: String,
    defaultValue: String = ""
) : Preference<String>(key, defaultValue) {
    override fun read(prefs: SharedPreferences): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    override fun write(prefs: SharedPreferences, value: String) {
        prefs.edit { putString(key, value) }
    }
}

class IntPreference(
    key: String,
    defaultValue: Int = 0
) : Preference<Int>(key, defaultValue) {
    override fun read(prefs: SharedPreferences): Int =
        prefs.getInt(key, defaultValue)

    override fun write(prefs: SharedPreferences, value: Int) {
        prefs.edit { putInt(key, value) }
    }
}