package com.deungbul.anshimgil.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Keeps the chosen mode on this phone only. It is never sent to the server for storage (NFR-07).
 */
class ModeRepository(private val context: Context) {
    private val modeKey = stringPreferencesKey("user_mode")

    /** null until the user picks a mode on first launch. */
    val mode: Flow<UserMode?> = context.settingsDataStore.data.map { UserMode.fromKey(it[modeKey]) }

    suspend fun save(mode: UserMode) {
        context.settingsDataStore.edit { it[modeKey] = mode.key }
    }
}
