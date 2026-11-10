package com.deungbul.anshimgil.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class TextSize(val label: String, val scale: Float) {
    SMALL("작게", 0.9f),
    NORMAL("보통", 1f),
    LARGE("크게", 1.15f),
}

data class AppSettings(
    val voiceGuide: Boolean = true,
    val autoConditions: Boolean = true,
    val textSize: TextSize = TextSize.NORMAL,
    /** Map items added on top of the mode's own (see [optionalMapItems]). */
    val extraMapItems: Set<String> = emptySet(),
    /** People texted on SOS or when leaving the route, in the order they were added. */
    val guardians: List<Guardian> = emptyList(),
    /** ↰/↱ signs at sharp turns on the guidance map; off unless the user turns them on. */
    val turnSigns: Boolean = false,
    /** The SOS button on the map screens; off unless the user turns it on. */
    val sosButton: Boolean = false,
    /** AI 비서 by voice back and forth: opening it listens, and after each answer it listens again (team, 11/10). */
    val voiceChat: Boolean = false,
)

/** Situations routing can react to; all are switched off when auto-detection is off. */
val ALL_CONDITION_KEYS: Set<String> = setOf("night", "rain", "heat", "cold", "dust")

/** App preferences, kept on this phone only. */
class SettingsRepository(private val context: Context) {
    private val voiceKey = booleanPreferencesKey("voice_guide")
    private val autoConditionsKey = booleanPreferencesKey("auto_conditions")
    private val textSizeKey = stringPreferencesKey("text_size")
    private val extraMapItemsKey = stringSetPreferencesKey("extra_map_items")
    // One line per guardian, kept in order (a string set would lose it).
    private val guardiansKey = stringPreferencesKey("guardians")
    private val turnSignsKey = booleanPreferencesKey("turn_signs")
    private val sosButtonKey = booleanPreferencesKey("sos_button")
    private val voiceChatKey = booleanPreferencesKey("voice_chat")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            voiceGuide = prefs[voiceKey] ?: true,
            autoConditions = prefs[autoConditionsKey] ?: true,
            // "아주 크게" was dropped; whoever chose it gets the largest size left.
            textSize = if (prefs[textSizeKey] == "EXTRA_LARGE") TextSize.LARGE
            else TextSize.entries.firstOrNull { it.name == prefs[textSizeKey] } ?: TextSize.NORMAL,
            extraMapItems = prefs[extraMapItemsKey].orEmpty(),
            guardians = prefs[guardiansKey].orEmpty().lines().mapNotNull { Guardian.decode(it) },
            turnSigns = prefs[turnSignsKey] ?: false,
            sosButton = prefs[sosButtonKey] ?: false,
            voiceChat = prefs[voiceChatKey] ?: false,
        )
    }

    suspend fun setVoiceChat(on: Boolean) {
        context.settingsDataStore.edit { it[voiceChatKey] = on }
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

    suspend fun addGuardian(guardian: Guardian) {
        context.settingsDataStore.edit {
            val now = it[guardiansKey].orEmpty().lines().mapNotNull { line -> Guardian.decode(line) }
            if (now.size < Guardian.MAX && now.none { g -> g.phone == guardian.phone }) {
                it[guardiansKey] = (now + guardian).joinToString("\n") { g -> g.encode() }
            }
        }
    }

    suspend fun removeGuardian(guardian: Guardian) {
        context.settingsDataStore.edit {
            val now = it[guardiansKey].orEmpty().lines().mapNotNull { line -> Guardian.decode(line) }
            it[guardiansKey] = (now - guardian).joinToString("\n") { g -> g.encode() }
        }
    }

    suspend fun setTurnSigns(on: Boolean) {
        context.settingsDataStore.edit { it[turnSignsKey] = on }
    }

    suspend fun setSosButton(on: Boolean) {
        context.settingsDataStore.edit { it[sosButtonKey] = on }
    }

    /** Back to the first-run choices. The mode, saved places and guardians are the user's own and stay. */
    suspend fun resetToDefaults() {
        context.settingsDataStore.edit {
            it.remove(voiceKey)
            it.remove(autoConditionsKey)
            it.remove(textSizeKey)
            it.remove(extraMapItemsKey)
            it.remove(turnSignsKey)
            it.remove(sosButtonKey)
        }
    }
}
