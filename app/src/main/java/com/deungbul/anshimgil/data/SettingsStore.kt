package com.deungbul.anshimgil.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** One small on-phone store for everything the app keeps locally (mode, saved places, recent searches). */
internal val Context.settingsDataStore by preferencesDataStore(name = "settings")
