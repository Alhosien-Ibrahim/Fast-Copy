package com.example.fastcopy.storage

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "fastcopy_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        val RAW_TEXT = stringPreferencesKey("raw_input")
        val LINES = stringPreferencesKey("saved_lines")
        val INDEX = intPreferencesKey("current_index")
        val THEME = booleanPreferencesKey("dark_mode")
        val IS_FLOATING_ENABLED = booleanPreferencesKey("floating_enabled")
    }

    val isFloatingEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[IS_FLOATING_ENABLED] ?: false }

    val inputText: Flow<String> = context.dataStore.data
        .map { it[RAW_TEXT] ?: "" }

    val savedLines: Flow<List<String>> = context.dataStore.data
        .map { it[LINES]?.split("§§") ?: emptyList() }

    val currentIndex: Flow<Int> = context.dataStore.data
        .map { it[INDEX] ?: 0 }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .map { it[THEME] ?: false }

    suspend fun saveFloatingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_FLOATING_ENABLED] = enabled }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun saveInput(text: String) {
        context.dataStore.edit { it[RAW_TEXT] = text }
    }

    suspend fun saveLines(lines: List<String>) {
        context.dataStore.edit { it[LINES] = lines.joinToString("§§") }
    }

    suspend fun saveIndex(index: Int) {
        context.dataStore.edit { it[INDEX] = index }
    }

    suspend fun saveTheme(isDark: Boolean) {
        context.dataStore.edit { it[THEME] = isDark }
    }
}