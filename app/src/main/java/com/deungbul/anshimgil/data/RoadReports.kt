package com.deungbul.anshimgil.data

import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 신고된 곳 (FR-49, team 결정 57 of 2026-11-10): the team's server view `road_report_active`
 * (safe-route 032_road_report_active.sql) decides which reports count right now, so the route engine, the server
 * and this map agree. The app reads it from `GET /api/reports/active` (server #151); [activeSpots] is the same
 * calculation on `GET /api/reports?bbox`, rule for rule (its tests are the team's own cases,
 * scripts/check_report_active.py), used only while a server without that window answers.
 *
 * 결정 57:
 *   - Same place = same kind within 30 m; the spot is named by the first report in it (the lowest number).
 *   - One report is enough to be valid; a spot is `confirmed` with 2 or more reports this period or one the team confirmed.
 *   - Period, the first report's day being day 1: 막는 물건·사고 1 day, 공사 7, 턱·급경사·보도 파손 30, 기타 7.
 *   - 공사 only: a report on the last day adds one day (again on the new last day, one more).
 *   - A report after a period has ended starts a new period that day.
 *   - Hidden reports do not count (the server's list leaves them out already).
 * The app's own rules of 11/10 (2 people or 3 reports, 24 hours, one person counted once) were dropped for these.
 */

/** Reports of one kind this close together are one place (결정 57). */
const val REPORT_SPOT_M = 30.0

/** During guidance, a spot this far ahead on the route at most is told… */
const val REPORT_AHEAD_M = 100.0

/** …when it is this close to the route. */
const val REPORT_NEAR_ROUTE_M = 30.0

/** One report from the server: [code] is the server's kind (steep, construction, curb, broken, obstacle, accident, other). */
data class RoadReport(
    val id: Long,
    val code: String,
    val label: String,
    val point: LatLon,
    val memo: String?,
    val teamConfirmed: Boolean,
    val createdAt: LocalDateTime?,
    val photoCount: Int,
)

/** A place with reports valid today, as the team's `road_report_active` gives it (`GET /api/reports/active`). */
data class ReportSpot(
    /** The first report in the place; also where the place is drawn. */
    val spotId: Long,
    val code: String,
    val label: String,
    val point: LatLon,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    /** Reports of this period (report_count). */
    val count: Int,
    val confirmed: Boolean,
    /** The newest report's time (last_at). */
    val latest: LocalDateTime? = null,
    val photoCount: Int = 0,
    /** The newest note (last_memo). */
    val lastMemo: String? = null,
)

/** The school server's times are Korean time. */
fun koreaNow(): LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

internal fun parseReports(json: JSONObject): List<RoadReport> {
    val list = json.optJSONArray("reports") ?: return emptyList()
    return (0 until list.length()).mapNotNull { i ->
        val r = list.optJSONObject(i) ?: return@mapNotNull null
        if (!r.has("lat") || !r.has("lon")) return@mapNotNull null
        val code = r.optString("type").ifBlank { "other" }
        RoadReport(
            id = r.optLong("id"),
            code = code,
            label = r.optString("typeName").takeUnless { r.isNull("typeName") || it.isBlank() }
                ?: ReportType.entries.firstOrNull { it.code == code }?.label ?: if (code == "accident") "사고" else "기타",
            point = LatLon(r.optDouble("lat"), r.optDouble("lon")),
            memo = r.optString("memo").takeUnless { r.isNull("memo") || it.isBlank() },
            teamConfirmed = r.optString("status") == "confirmed",
            createdAt = r.optString("createdAt").takeUnless { r.isNull("createdAt") || it.isBlank() }
                ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            photoCount = r.optJSONArray("photoUrls")?.length() ?: 0,
        )
    }
}

/** Days a report keeps a place valid, by kind (결정 57). */
fun reportDays(code: String): Long = when (code) {
    "obstacle", "accident" -> 1
    "construction" -> 7
    "curb", "steep", "broken" -> 30
    else -> 7
}

/**
 * The places valid on [today], worked out as `road_report_active` does: each report belongs to the lowest-numbered
 * report of its kind within 30 m of it; a place's days are walked in order to find its latest period; the place shows
 * while that period has not ended, with the reports of that period.
 */
fun activeSpots(reports: List<RoadReport>, today: LocalDate = koreaNow().toLocalDate()): List<ReportSpot> {
    val dated = reports.filter { it.createdAt != null && !it.createdAt.toLocalDate().isAfter(today) }
    val spotOf = dated.associate { r ->
        r.id to dated.filter { it.code == r.code && sphereMeters(it.point, r.point) <= REPORT_SPOT_M }.minOf { it.id }
    }
    return dated.groupBy { spotOf.getValue(it.id) }.mapNotNull { (spotId, inSpot) ->
        val first = dated.first { it.id == spotId }
        val days = reportDays(first.code)
        var start: LocalDate? = null
        var end: LocalDate? = null
        for (d in inSpot.map { it.createdAt!!.toLocalDate() }.distinct().sorted()) {
            val e = end
            if (start == null || e == null || d.isAfter(e)) {
                start = d
                end = d.plusDays(days - 1)
            } else if (first.code == "construction" && d == e) {
                end = e.plusDays(1)
            }
        }
        val s = start ?: return@mapNotNull null
        val e = end ?: return@mapNotNull null
        if (e.isBefore(today)) return@mapNotNull null
        val inPeriod = inSpot.filter { val d = it.createdAt!!.toLocalDate(); !d.isBefore(s) && !d.isAfter(e) }
            .sortedWith(compareBy<RoadReport> { it.createdAt }.thenBy { it.id })
        ReportSpot(
            spotId = spotId, code = first.code, label = first.label, point = first.point,
            startsOn = s, endsOn = e, count = inPeriod.size,
            confirmed = inPeriod.size >= 2 || inPeriod.any { it.teamConfirmed },
            latest = inPeriod.mapNotNull { it.createdAt }.maxOrNull(),
            photoCount = inPeriod.sumOf { it.photoCount },
            lastMemo = inPeriod.lastOrNull { !it.memo.isNullOrBlank() }?.memo,
        )
    }.sortedBy { it.spotId }
}

/** Great-circle meters on MySQL's ST_Distance_Sphere sphere, so "within 30 m" means the same as on the server. */
private fun sphereMeters(a: LatLon, b: LatLon): Double {
    val r = 6_370_986.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(h))
}

/** "20분 전", "3시간 전", "2일 전". */
fun agoText(at: LocalDateTime?, now: LocalDateTime = koreaNow()): String? {
    val min = Duration.between(at ?: return null, now).toMinutes().coerceAtLeast(0)
    return when {
        min < 1 -> "방금 전"
        min < 60 -> "${min}분 전"
        min < 24 * 60 -> "${min / 60}시간 전"
        else -> "${min / (24 * 60)}일 전"
    }
}

/** The tag on the map: "공사 2". */
fun reportSpotTag(spot: ReportSpot): String = "${spot.label} ${spot.count}"

/** The card on tapping it: "공사 신고 2건 · 마지막 신고 20분 전 · 확인됨". */
fun reportSpotTitle(spot: ReportSpot, now: LocalDateTime = koreaNow()): String =
    "${spot.label} 신고 ${spot.count}건" + (agoText(spot.latest, now)?.let { " · 마지막 신고 $it" } ?: "") +
        if (spot.confirmed) " · 확인됨" else ""

/** Until when it shows: "11월 16일까지 지도에 보여요." */
fun reportSpotUntil(spot: ReportSpot): String = "${spot.endsOn.monthValue}월 ${spot.endsOn.dayOfMonth}일까지 지도에 보여요."

/**
 * Said during guidance, in the team's words (결정 57: a spot the route could not go round is passed with
 * "앞에 ○○ 신고가 있는 곳을 지나요"). [aheadM] only decides when it is said.
 */
@Suppress("UNUSED_PARAMETER")
fun reportAheadText(spot: ReportSpot, aheadM: Int): String = "앞에 ${spot.label} 신고가 있는 곳을 지나요."

/** Spots ahead on the route, nearest first, as (spot, meters ahead); [onRoute] gives meters along and off the route. */
fun reportSpotsAhead(spots: List<ReportSpot>, progressM: Double, onRoute: (LatLon) -> Pair<Double, Double>): List<Pair<ReportSpot, Int>> =
    spots.mapNotNull { s ->
        val (along, off) = onRoute(s.point)
        val ahead = along - progressM
        if (ahead < 0 || ahead > REPORT_AHEAD_M || off > REPORT_NEAR_ROUTE_M) null else s to ahead.toInt()
    }.sortedBy { it.second }

/** The server's view rows (`GET /api/reports/active`). */
internal fun parseActiveSpots(json: JSONObject): List<ReportSpot> {
    val list = json.optJSONArray("spots") ?: return emptyList()
    fun str(o: JSONObject, k: String) = o.optString(k).takeUnless { o.isNull(k) || it.isBlank() }
    return (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        val code = str(o, "reportType") ?: "other"
        val starts = str(o, "startsOn")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return@mapNotNull null
        val ends = str(o, "endsOn")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return@mapNotNull null
        ReportSpot(
            spotId = o.optLong("spotId"),
            code = code,
            label = ReportType.entries.firstOrNull { it.code == code }?.label ?: "기타",
            point = LatLon(o.optDouble("lat"), o.optDouble("lon")),
            startsOn = starts,
            endsOn = ends,
            count = o.optInt("reportCount", 1),
            confirmed = o.optBoolean("confirmed") || o.optInt("confirmed", 0) == 1,
            latest = str(o, "lastAt")?.let { runCatching { LocalDateTime.parse(it.take(19).replace(' ', 'T')) }.getOrNull() },
            photoCount = o.optInt("photoCount"),
            lastMemo = str(o, "lastMemo"),
        )
    }
}

object RoadReportRepository {
    /**
     * The places valid today in [area]: the server's view (`/api/reports/active`), or, from a server without it,
     * the same calculation here on its report list. Throws [ApiException].
     */
    suspend fun activeWithin(area: Bbox): List<ReportSpot> {
        val box = "%.6f,%.6f,%.6f,%.6f".format(java.util.Locale.US, area.west, area.south, area.east, area.north)
        return try {
            parseActiveSpots(ServerApi.get("/api/reports/active", mapOf("bbox" to box)))
        } catch (e: ApiException) {
            if (e.code == "NETWORK") throw e
            activeSpots(within(area))
        }
    }

    /** Open and confirmed reports in [area], newest first. Throws [ApiException]. */
    suspend fun within(area: Bbox): List<RoadReport> = parseReports(
        ServerApi.get(
            "/api/reports",
            mapOf("bbox" to "%.6f,%.6f,%.6f,%.6f".format(java.util.Locale.US, area.west, area.south, area.east, area.north)),
        ),
    )
}
