package com.deungbul.anshimgil.data

import android.content.Context
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * 신고 쌓인 곳 (11/10): reports from everyone (`GET /api/reports?bbox`) are drawn on the map only where they pile up,
 * so one wrong report does not mark a street.
 *   - Reports of one kind within [REPORT_SPOT_M] of each other make one spot.
 *   - Only the last [REPORT_KEEP_HOURS] hours count; older ones drop off by themselves (works end, things get moved).
 *   - A spot shows from 2 reports by different people; when one person reported again at the same spot, from 3.
 *   - A report the team confirmed shows on its own.
 * The server does not keep who reported (no login); it may send a per-person key as `reporter` later. Until then
 * this phone knows its own reports ([MyReports]) and counts every other report as a different person.
 */

/** Reports of one kind this close together are one spot. */
const val REPORT_SPOT_M = 30.0

/** Only reports from the last this many hours count. */
const val REPORT_KEEP_HOURS = 24L

/** A spot shows from this many reports by different people… */
const val REPORT_SPOT_PEOPLE = 2

/** …or this many reports in all (one person reporting again). */
const val REPORT_SPOT_TOTAL = 3

/** During guidance, a spot this far ahead on the route at most is told… */
const val REPORT_AHEAD_M = 100.0

/** …when it is this close to the route. */
const val REPORT_NEAR_ROUTE_M = 30.0

/** One report from the server. [reporter]: the server's key for who sent it, when it sends one. */
data class RoadReport(
    val id: Long,
    val type: ReportType,
    val point: LatLon,
    val memo: String?,
    val confirmed: Boolean,
    val createdAt: LocalDateTime?,
    val photoCount: Int,
    val reporter: String? = null,
)

/** Reports of one kind piled up in one place. */
data class ReportSpot(val type: ReportType, val point: LatLon, val reports: List<RoadReport>) {
    val count: Int get() = reports.size
    val latest: LocalDateTime? get() = reports.mapNotNull { it.createdAt }.maxOrNull()
    val memos: List<String> get() = reports.mapNotNull { it.memo?.takeIf { m -> m.isNotBlank() } }
}

/** The school server's times are Korean time. */
fun koreaNow(): LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

internal fun parseReports(json: JSONObject): List<RoadReport> {
    val list = json.optJSONArray("reports") ?: return emptyList()
    return (0 until list.length()).mapNotNull { i ->
        val r = list.optJSONObject(i) ?: return@mapNotNull null
        val type = ReportType.entries.firstOrNull { it.code == r.optString("type") } ?: ReportType.OTHER
        if (!r.has("lat") || !r.has("lon")) return@mapNotNull null
        RoadReport(
            id = r.optLong("id"),
            type = type,
            point = LatLon(r.optDouble("lat"), r.optDouble("lon")),
            memo = r.optString("memo").takeUnless { r.isNull("memo") || it.isBlank() },
            confirmed = r.optString("status") == "confirmed",
            createdAt = r.optString("createdAt").takeUnless { r.isNull("createdAt") || it.isBlank() }
                ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
            photoCount = r.optJSONArray("photoUrls")?.length() ?: 0,
            reporter = r.optString("reporter").takeUnless { r.isNull("reporter") || it.isBlank() },
        )
    }
}

/** Who sent [r], for counting people: the server's key, this phone, or (unknown) a person of its own. */
private fun personOf(r: RoadReport, mine: Set<Long>): String = r.reporter ?: if (r.id in mine) "me" else "report-${r.id}"

/** Whether a spot is drawn: confirmed, or 2 people, or 3 reports. */
fun spotShown(spot: ReportSpot, mine: Set<Long> = emptySet()): Boolean =
    spot.reports.any { it.confirmed } ||
        spot.reports.map { personOf(it, mine) }.distinct().size >= REPORT_SPOT_PEOPLE ||
        spot.count >= REPORT_SPOT_TOTAL

/**
 * The spots to draw from [reports]: those of the last [REPORT_KEEP_HOURS] hours, grouped by kind within
 * [REPORT_SPOT_M], kept when [spotShown]. A spot sits in the middle of its reports.
 */
fun reportSpots(reports: List<RoadReport>, now: LocalDateTime = koreaNow(), mine: Set<Long> = emptySet()): List<ReportSpot> {
    val recent = reports.filter { r ->
        val at = r.createdAt ?: return@filter false
        !at.isAfter(now.plusMinutes(5)) && Duration.between(at, now).toHours() < REPORT_KEEP_HOURS
    }
    val spots = mutableListOf<ReportSpot>()
    recent.groupBy { it.type }.forEach { (type, ofType) ->
        // Oldest first, each joining the first spot whose middle is close enough.
        val groups = mutableListOf<MutableList<RoadReport>>()
        ofType.sortedBy { it.createdAt }.forEach { r ->
            val near = groups.firstOrNull { g -> metersBetween(middle(g), r.point) <= REPORT_SPOT_M }
            if (near != null) near += r else groups += mutableListOf(r)
        }
        groups.forEach { g -> spots += ReportSpot(type, middle(g), g.toList()) }
    }
    return spots.filter { spotShown(it, mine) }.sortedByDescending { it.count }
}

private fun middle(rs: List<RoadReport>) = LatLon(rs.map { it.point.lat }.average(), rs.map { it.point.lon }.average())

/** "20분 전", "3시간 전". */
fun agoText(at: LocalDateTime?, now: LocalDateTime = koreaNow()): String? {
    val min = Duration.between(at ?: return null, now).toMinutes().coerceAtLeast(0)
    return when {
        min < 1 -> "방금"
        min < 60 -> "${min}분 전"
        else -> "${min / 60}시간 전"
    }
}

/** The tag on the map: "공사 3". */
fun reportSpotTag(spot: ReportSpot): String = "${spot.type.label} ${spot.count}"

/** The card on tapping it: "공사 신고 3건 · 마지막 신고 20분 전". */
fun reportSpotTitle(spot: ReportSpot, now: LocalDateTime = koreaNow()): String =
    "${spot.type.label} 신고 ${spot.count}건" + (agoText(spot.latest, now)?.let { " · 마지막 신고 $it" } ?: "") +
        if (spot.reports.any { it.confirmed }) " · 확인됨" else ""

/** Said during guidance: "앞 60m에 공사 신고가 3건 있어요. 조심해서 가세요." */
fun reportAheadText(spot: ReportSpot, aheadM: Int): String =
    (if (aheadM < 10) "바로 앞에" else "앞 ${aheadM}m에") + " ${spot.type.label} 신고가 ${spot.count}건 있어요. 조심해서 가세요."

/** Spots ahead on the route, nearest first, as (spot, meters ahead); [onRoute] gives meters along and off the route. */
fun reportSpotsAhead(spots: List<ReportSpot>, progressM: Double, onRoute: (LatLon) -> Pair<Double, Double>): List<Pair<ReportSpot, Int>> =
    spots.mapNotNull { s ->
        val (along, off) = onRoute(s.point)
        val ahead = along - progressM
        if (ahead < 0 || ahead > REPORT_AHEAD_M || off > REPORT_NEAR_ROUTE_M) null else s to ahead.toInt()
    }.sortedBy { it.second }

object RoadReportRepository {
    /** Open and confirmed reports in [area], newest first. Throws [ApiException]. */
    suspend fun within(area: Bbox): List<RoadReport> = parseReports(
        ServerApi.get(
            "/api/reports",
            mapOf("bbox" to "%.6f,%.6f,%.6f,%.6f".format(java.util.Locale.US, area.west, area.south, area.east, area.north)),
        ),
    )
}

/** The ids of the reports sent from this phone, so they count as one person. Kept on the phone only. */
object MyReports {
    private const val PREFS = "my_reports"
    private const val KEY = "ids"

    fun ids(context: Context): Set<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toSet()

    fun add(context: Context, id: Long) {
        if (id <= 0) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Only the latest ones matter (a day's worth); keep the set small.
        val ids = (ids(context) + id).sortedDescending().take(50).map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY, ids).apply()
    }
}
