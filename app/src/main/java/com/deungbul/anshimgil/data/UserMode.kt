package com.deungbul.anshimgil.data

/**
 * Who is walking. [key] is the value shared with the server (`/api/route` mode).
 */
enum class UserMode(val key: String) {
    CHILD("child"),
    SENIOR("senior"),
    DISABLED("disabled"),
    GENERAL("general");

    companion object {
        fun fromKey(key: String?): UserMode? = entries.firstOrNull { it.key == key }
    }
}
