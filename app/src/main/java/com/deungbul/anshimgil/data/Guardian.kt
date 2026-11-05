package com.deungbul.anshimgil.data

/** Someone who gets a text when the walker presses SOS or leaves the planned route (귀가 지킴이). */
data class Guardian(val name: String, val phone: String) {
    /** Stored as one string per guardian; '|' cannot appear in a phone number. */
    fun encode(): String = "${name.replace("|", " ")}|$phone"

    companion object {
        const val MAX = 3

        fun decode(text: String): Guardian? {
            val name = text.substringBefore('|', "").trim()
            val phone = text.substringAfter('|', "").trim()
            return if (name.isEmpty() || phone.isEmpty()) null else Guardian(name, phone)
        }

        /** Digits only (a leading + kept), or null when it cannot be a phone number. */
        fun cleanPhone(input: String): String? {
            val trimmed = input.trim()
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length !in 3..15) return null
            return if (trimmed.startsWith("+")) "+$digits" else digits
        }
    }
}

/**
 * The text sent to guardians. The location goes as a Kakao Map link that opens on any phone;
 * it leaves the phone only in this text, which the walker sends themselves.
 */
fun guardianMessage(reason: GuardianAlert, here: LatLon?): String {
    val head = when (reason) {
        GuardianAlert.SOS -> "[세잎지도] 도움이 필요해요! SOS를 눌렀어요."
        GuardianAlert.OFF_ROUTE -> "[세잎지도] 정한 길에서 벗어났어요."
    }
    val where = here?.let { "\n지금 위치: https://map.kakao.com/link/map/지금위치,%.6f,%.6f".format(java.util.Locale.US, it.lat, it.lon) }
        ?: "\n지금 위치는 찾지 못했어요."
    return head + where
}

enum class GuardianAlert { SOS, OFF_ROUTE }
