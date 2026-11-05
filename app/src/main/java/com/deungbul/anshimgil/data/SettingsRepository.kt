package com.deungbul.anshimgil.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class TextSize(val label: String, val scale: Float) {
    NORMAL("보통", 1f),
    LARGE("크게", 1.15f),
    EXTRA_LARGE("아주 크게", 1.3f),
}

data class AppSettings(
    val voiceGuide: Boolean = true,
    val autoConditions: Boolean = true,
    val textSize: TextSize = TextSize.NORMAL,
    /** Map items added on top of the mode's own (see [optionalMapItems]). */
    val extraMapItems: Set<String> = emptySet(),
)

/** Situations routing can react to; all are switched off when auto-detection is off. */
val ALL_CONDITION_KEYS: Set<String> = setOf("night", "rain", "heat", "cold", "dust")

/** App preferences, kept on this phone only. */
class SettingsRepository(private val context: Context) {
    private val voiceKey = booleanPreferencesKey("voice_guide")
    private val autoConditionsKey = booleanPreferencesKey("auto_conditions")
    private val textSizeKey = stringPreferencesKey("text_size")
    private val extraMapItemsKey = stringSetPreferencesKey("extra_map_items")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            voiceGuide = prefs[voiceKey] ?: true,
            autoConditions = prefs[autoConditionsKey] ?: true,
            textSize = TextSize.entries.firstOrNull { it.name == prefs[textSizeKey] } ?: TextSize.NORMAL,
            extraMapItems = prefs[extraMapItemsKey].orEmpty(),
        )
    }

    suspend fun setVoiceGuide(on: Boolean) {
        context.settingsDataStore.edit { it[voiceKey] = on }
    }

    suspend fun setAutoConditions(on: Boolean) {
        context.settingsDataStore.edit { it[autoConditionsKey] = on }
    }

    suspend fun setTextSize(size: TextSize) {
        context.settingsDataStore.edit { it[textSizeKey] = size.name }
    }

    suspend fun setExtraMapItem(key: String, on: Boolean) {
        context.settingsDataStore.edit {
            val now = it[extraMapItemsKey].orEmpty()
            it[extraMapItemsKey] = if (on) now + key else now - key
        }
    }
}
