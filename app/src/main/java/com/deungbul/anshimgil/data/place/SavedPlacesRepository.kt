package com.deungbul.anshimgil.data.place

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import com.deungbul.anshimgil.data.route.toJson
import com.deungbul.anshimgil.data.user.settingsDataStore

/** A named shortcut such as 집 or 학교. [place] is null until the user picks one. */
data class Favorite(val name: String, val place: Place?, val builtIn: Boolean)

private val DEFAULT_FAVORITES = listOf("집", "학교", "직장").map { Favorite(it, null, builtIn = true) }
private const val MAX_RECENTS = 10

/**
 * Favorites and recent searches, kept on this phone only (they can include a home address).
 */
class SavedPlacesRepository(private val context: Context) {
    private val favoritesKey = stringPreferencesKey("favorites")
    private val recentsKey = stringPreferencesKey("recent_places")

    val favorites: Flow<List<Favorite>> = context.settingsDataStore.data.map { prefs ->
        prefs[favoritesKey]?.let(::favoritesFromJson) ?: DEFAULT_FAVORITES
    }

    val recents: Flow<List<Place>> = context.settingsDataStore.data.map { placesFromJsonArray(it[recentsKey]) }

    suspend fun setFavorite(name: String, place: Place) = updateFavorites { list ->
        if (list.any { it.name == name }) {
            list.map { if (it.name == name) it.copy(place = place) else it }
        } else {
            list + Favorite(name, place, builtIn = false)
        }
    }

    /** Built-in shortcuts are only emptied; ones the user added are removed. */
    suspend fun removeFavorite(name: String) = updateFavorites { list ->
        list.mapNotNull { if (it.name != name) it else if (it.builtIn) it.copy(place = null) else null }
    }

    suspend fun addRecent(place: Place) {
        context.settingsDataStore.edit { prefs ->
            val current = placesFromJsonArray(prefs[recentsKey])
            val updated = (listOf(place.copy(distanceM = null)) + current.filterNot { it.sameAs(place) })
                .take(MAX_RECENTS)
            prefs[recentsKey] = updated.toJsonArrayString()
        }
    }

    private suspend fun updateFavorites(change: (List<Favorite>) -> List<Favorite>) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[favoritesKey]?.let(::favoritesFromJson) ?: DEFAULT_FAVORITES
            prefs[favoritesKey] = favoritesToJson(change(current))
        }
    }
}

private fun Place.sameAs(other: Place) =
    (id != null && id == other.id) || (name == other.name && address == other.address)

private fun favoritesToJson(list: List<Favorite>): String = JSONArray(
    list.map { favorite ->
        JSONObject()
            .put("name", favorite.name)
            .put("builtIn", favorite.builtIn)
            .put("place", favorite.place?.toJson() ?: JSONObject.NULL)
    },
).toString()

private fun favoritesFromJson(text: String): List<Favorite> {
    val array = JSONArray(text)
    return (0 until array.length()).map { index ->
        val json = array.getJSONObject(index)
        Favorite(
            name = json.getString("name"),
            place = if (json.isNull("place")) null else Place.fromJson(json.getJSONObject("place")),
            builtIn = json.optBoolean("builtIn"),
        )
    }
}
