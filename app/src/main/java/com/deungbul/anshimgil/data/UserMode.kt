package com.deungbul.anshimgil.data

/**
 * Who is walking. [key] is the value shared with the server (`/api/route` mode).
 */
enum class UserMode(val key: String) {
    CHILD("child"),
    SENIOR("senior"),
    DISABLED("wheelchair"),
    GENERAL("adult");

    companion object {
        // Keys saved before the rename to match the route engine.
        private val oldKeys = mapOf("disabled" to DISABLED, "general" to GENERAL)

        fun fromKey(key: String?): UserMode? = entries.firstOrNull { it.key == key } ?: oldKeys[key]
    }
}
