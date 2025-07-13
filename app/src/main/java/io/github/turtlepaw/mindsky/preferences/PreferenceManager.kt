package io.github.turtlepaw.mindsky.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.StateFlow

class PreferenceManager(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // Find the preference that changed and invalidate its cache
        AppPrefs.ALL.find { it.key == key }?.invalidateCache(sharedPrefs)
    }

    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    // Get values (cached)
    fun <T> get(pref: Preference<T>): T = pref.getValue(sharedPrefs)

    // Set values
    fun <T> set(pref: Preference<T>, value: T) = pref.setValue(sharedPrefs, value)

    // Get StateFlow for reactive updates
    fun <T> getStateFlow(pref: Preference<T>): StateFlow<T> {
        // Ensure cache is loaded
        pref.getValue(sharedPrefs)
        return pref.stateFlow
    }

    // For your UI settings lib - just the key
    fun getKey(pref: Preference<*>): String = pref.key

    fun getSharedPreferences(): SharedPreferences = sharedPrefs

    fun cleanup() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}