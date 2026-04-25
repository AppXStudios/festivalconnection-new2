package com.appxstudios.festivalconnection.ui.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Reactive Compose State backed by a String SharedPreferences value.
 * Recomposes whenever the underlying preference changes (from any source).
 */
@Composable
fun rememberStringPref(
    context: Context,
    prefsName: String,
    key: String,
    default: String = ""
): State<String> {
    val prefs = remember(prefsName) { context.getSharedPreferences(prefsName, Context.MODE_PRIVATE) }
    val state = remember(prefs, key) { mutableStateOf(prefs.getString(key, default) ?: default) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, k ->
            if (k == key) state.value = p.getString(key, default) ?: default
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

/**
 * Reactive Compose State backed by a String-Set SharedPreferences value.
 * Recomposes whenever the underlying preference changes (from any source).
 */
@Composable
fun rememberStringSetPref(
    context: Context,
    prefsName: String,
    key: String
): State<Set<String>> {
    val prefs = remember(prefsName) { context.getSharedPreferences(prefsName, Context.MODE_PRIVATE) }
    val state = remember(prefs, key) {
        mutableStateOf(prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet())
    }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, k ->
            if (k == key) state.value = (p.getStringSet(key, emptySet()) ?: emptySet()).toSet()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
