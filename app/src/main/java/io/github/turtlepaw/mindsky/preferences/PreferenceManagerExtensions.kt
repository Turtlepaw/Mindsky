package io.github.turtlepaw.mindsky.preferences

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.MutablePreferences
import me.zhanghai.compose.preference.Preferences

@OptIn(DelicateCoroutinesApi::class)
fun PreferenceManager.getPreferenceFlow(): MutableStateFlow<Preferences> =
    MutableStateFlow(preferences).also {
        GlobalScope.launch { it.drop(1).collect { preferences = it } }
    }

private var PreferenceManager.preferences: Preferences
    get() = MapPreferences(getAllPreferences())
    set(value) {
        value.asMap().forEach { (key, newValue) ->
            // Find the preference definition and update it
            AppPrefs.ALL.find { it.key == key }?.let { pref ->
                when (newValue) {
                    is Boolean -> {
                        if (pref is BooleanPreference) {
                            set(pref, newValue)
                        }
                    }

                    is Int -> {
                        if (pref is IntPreference) {
                            set(pref, newValue)
                        } else if (pref is EnumPreference<*>) {
                            // Handle enum as ordinal
                            @Suppress("UNCHECKED_CAST")
                            val enumPref = pref as EnumPreference<Enum<*>>
                            val enumValues = enumPref.defaultValue::class.java.enumConstants
                            enumValues?.getOrNull(newValue)?.let { enumValue ->
                                @Suppress("UNCHECKED_CAST")
                                set(pref as Preference<Any>, enumValue)
                            }
                        }
                    }

                    is String -> {
                        if (pref is StringPreference) {
                            set(pref, newValue)
                        }
                    }

                    is Long -> {
                        if (pref is IntPreference) {
                            set(pref, newValue.toInt())
                        }
                    }

                    is Float -> {
                        // Handle float if you add FloatPreference later
                    }

                    is Set<*> -> {
                        // Handle string sets if you add StringSetPreference later
                    }
                }
            }
        }
    }

// Helper to get all current preference values as a map
private fun PreferenceManager.getAllPreferences(): Map<String, Any> {
    return AppPrefs.ALL.associate { pref ->
        val value = when (pref) {
            is BooleanPreference -> get(pref)
            is IntPreference -> get(pref)
            is StringPreference -> get(pref)
            is EnumPreference<*> -> (get(pref) as Enum<*>).ordinal
            else -> get(pref)
        }
        pref.key to value
    }
}

// MapPreferences implementation
private class MapPreferences(private val map: Map<String, Any>) : Preferences {
    override fun asMap(): Map<String, Any> = map

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String): T? = map[key] as? T

    override fun toMutablePreferences(): MutablePreferences =
        MutableMapPreferences(map.toMutableMap())
}

private class MutableMapPreferences(private val map: MutableMap<String, Any>) : MutablePreferences {
    override fun asMap(): Map<String, Any> = map

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String): T? = map[key] as? T

    override fun toMutablePreferences(): MutablePreferences = this

    override fun <T> set(key: String, value: T?) {
        if (value != null) {
            map[key] = value as Any
        } else {
            map.remove(key)
        }
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun clear() {
        map.clear()
    }
}