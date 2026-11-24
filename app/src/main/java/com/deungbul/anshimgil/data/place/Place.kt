package com.deungbul.anshimgil.data.place

import org.json.JSONObject
import com.deungbul.anshimgil.data.route.toJson

/** A searchable place. Field names follow the server's `/api/places` response. */
data class Place(
    val id: String?,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    /** Meters from the user; null when the app did not send its position. */
    val distanceM: Int?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("address", address)
        .put("lat", lat)
        .put("lon", lon)
        .put("distanceM", distanceM)

    companion object {
        fun fromJson(json: JSONObject) = Place(
            id = json.optString("id").ifBlank { null },
            name = json.getString("name"),
            address = json.optString("address"),
            lat = json.getDouble("lat"),
            lon = json.getDouble("lon"),
            distanceM = if (json.isNull("distanceM")) null else json.optInt("distanceM"),
        )
    }
}
