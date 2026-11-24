package com.deungbul.anshimgil.data.user

import org.json.JSONObject
import com.deungbul.anshimgil.data.server.ApiException
import com.deungbul.anshimgil.data.server.ServerApi

/*
 * 내 정보 (팀 할 일 m06, 11/9): what the server keeps about how this walker gets about — stairs, how far a safer
 * way may go round, how often they rest, walking or transit — which the route engine now uses
 * (server b056c99), shown and changed in one screen. The words for keeping trips and deleting everything are the
 * team's (docs/assistant/consent.md §1, §5).
 */

/** GET /api/me, as far as the screen needs it. Null fields: not set yet (the server's defaults apply). */
data class MyInfo(
    val known: Boolean,
    val mode: String?,
    val stairsOk: Int?,
    val maxDetour: Double?,
    val restEveryM: Int?,
    val preferTransit: Int?,
    val consentTrips: Boolean,
    val trips: Int,
    val tempMode: String?,
    val tempUntil: String?,
)

fun parseMyInfo(j: JSONObject): MyInfo {
    fun str(k: String) = j.optString(k).takeIf { !j.isNull(k) && it.isNotBlank() }
    fun int(k: String) = if (j.isNull(k) || !j.has(k)) null else j.optInt(k)
    return MyInfo(
        known = j.optBoolean("known"),
        mode = str("modeNow") ?: str("mode"),
        stairsOk = int("stairsOk"),
        maxDetour = if (j.isNull("maxDetour") || !j.has("maxDetour")) null else j.optDouble("maxDetour"),
        restEveryM = int("restEveryM"),
        preferTransit = int("preferTransit"),
        consentTrips = j.optBoolean("consentTrips"),
        trips = j.optInt("trips"),
        tempMode = str("tempMode").takeIf { j.optBoolean("tempActive") },
        tempUntil = str("tempUntil").takeIf { j.optBoolean("tempActive") },
    )
}

/** The answers each question offers, in the server's values, with the words shown. */
val STAIRS_CHOICES = listOf(0 to "못 가요", 1 to "몇 칸은 괜찮아요", 2 to "괜찮아요")
val DETOUR_CHOICES = listOf(1.0 to "가장 짧은 길로", 1.2 to "조금 돌아도 돼요", 1.5 to "꽤 돌아도 돼요", 2.0 to "많이 돌아도 돼요")
val REST_CHOICES = listOf(200 to "200m마다", 400 to "400m마다", 700 to "700m마다", 1000 to "1km마다", 2000 to "안 쉬어도 돼요")
val TRANSIT_CHOICES = listOf(0 to "걷는 게 좋아요", 1 to "상관없어요", 2 to "대중교통이 좋아요")

/** The choice nearest to [value] (the server may keep any number in its range). */
fun <T : Number> nearestChoice(value: T?, choices: List<Pair<T, String>>): T? =
    value?.let { v -> choices.minByOrNull { kotlin.math.abs(it.first.toDouble() - v.toDouble()) }?.first }

/** consent.md §1: keeping the trips. */
const val CONSENT_TITLE = "다닌 기록을 남겨도 될까요?"
const val CONSENT_BODY = "기록을 남기면 비서가 더 잘 도와드려요.\n" +
    "예: \"비 오는 날엔 보통 버스를 타셨어요. 오늘도 버스로 안내할까요?\"\n\n" +
    "남기는 것: 간 곳의 이름, 나간 요일과 시간대(30분 단위), 걸어갔는지 버스를 탔는지, 그날 날씨, 다녀와서 남긴 한마디\n\n" +
    "남기지 않는 것: 이름, 전화번호, 집 주소, 지나온 길, 정확한 시각\n\n" +
    "언제든 [내 기록 지우기]를 누르면 바로 모두 지워져요."
const val CONSENT_YES = "네, 남길게요"
const val CONSENT_NO = "아니요"
/** consent.md §1 [아니요] (11-10 고침: 동의하지 않으면 휴대폰 안 습관 기록도 남기지 않음, #101). */
const val CONSENT_NO_REPLY = "알겠어요. 기록은 남기지 않을게요. 길찾기는 그대로 다 쓸 수 있어요."

/** consent.md §5: deleting everything. */
const val DELETE_ALL_TITLE = "비서가 기억한 것을 모두 지울까요?"
const val DELETE_ALL_BODY = "다닌 기록, 걸음 속도, 몸 상태, 지도 설정이 모두 지워져요. 되돌릴 수 없어요."
const val DELETE_ALL_DONE = "모두 지웠어요. 처음 오신 것처럼 다시 여쭤볼게요."

class MyInfoRepository(private val guestId: suspend () -> String) {
    /** Throws [ApiException]. */
    suspend fun load(): MyInfo = parseMyInfo(ServerApi.get("/api/me", mapOf("guestId" to guestId())))

    /** Changes one or more fields ([fields] in the server's names); the info as it is now. Throws [ApiException]. */
    suspend fun update(mode: UserMode, fields: JSONObject): MyInfo {
        val body = JSONObject().put("guestId", guestId()).put("mode", mode.key)
        fields.keys().forEach { body.put(it, fields.get(it)) }
        return parseMyInfo(ServerApi.patch("/api/me", body))
    }

    /**
     * Tells the server this phone's guest number and mode (진행표 10): the number is made on the phone and kept in the
     * server's user_profile, so the server's mode is the phone's (it said 어린이 while the phone was 어르신, 11/9).
     * No trips or places go with it. Throws [ApiException].
     */
    suspend fun syncMode(mode: UserMode) {
        ServerApi.patch("/api/me", JSONObject().put("guestId", guestId()).put("mode", mode.key))
    }

    /** consent.md §5. Throws [ApiException]. */
    suspend fun deleteAll() {
        ServerApi.delete("/api/me", mapOf("guestId" to guestId()))
    }
}
