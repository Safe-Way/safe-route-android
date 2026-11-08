package com.deungbul.anshimgil.data

import com.deungbul.anshimgil.location.PositionFilter
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 길안내 재현 (11/8): real walking routes around 조선대·동구 (OpenStreetMap's foot routing, saved as OSRM JSON in
 * the folder named by the system property `replay.dir`; skipped without it) walked by a made-up GPS — noise,
 * jumps, a wrong turn, a bus ride, waiting at a crossing — through the same steps the guidance screen takes:
 * [PositionFilter] → [MotionTracker] → [Guidance.update] with the screen's backtrack hold. The turns the app would
 * announce are compared with the route's own turn-by-turn steps. Prints a report; it checks, it does not fail.
 */
class GuideReplayTest {
    private val offM = (System.getenv("REPLAY_OFF_M") ?: "25").toInt()
    private val dir = (System.getProperty("replay.dir") ?: System.getenv("REPLAY_DIR"))?.let(::File)

    private data class Step(val at: LatLon, val modifier: String, val type: String)
    private data class Route(val name: String, val path: List<LatLon>, val steps: List<Step>, val distance: Double)

    private fun load(f: File): Route {
        val r = JSONObject(f.readText()).getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val path = (0 until coords.length()).map { coords.getJSONArray(it).let { c -> LatLon(c.getDouble(1), c.getDouble(0)) } }
        val steps = mutableListOf<Step>()
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val s = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until s.length()) {
                val m = s.getJSONObject(i).getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                steps += Step(LatLon(loc.getDouble(1), loc.getDouble(0)), m.optString("modifier"), m.getString("type"))
            }
        }
        return Route(f.nameWithoutExtension, path, steps, r.getDouble("distance"))
    }

    /** The turns the app announces along the route: (meters along, LEFT/RIGHT). */
    private fun appTurns(g: Guidance): List<Pair<Double, Turn>> {
        val out = mutableListOf<Pair<Double, Turn>>()
        var p = 0.0
        while (p < g.totalM) {
            val s = g.atProgress(p)
            if (s.nextTurn == Turn.LEFT || s.nextTurn == Turn.RIGHT) {
                val at = p + s.metersToNextTurn
                if (out.none { abs(it.first - at) < 1.5 }) out += at to s.nextTurn
            }
            p += 1.0
        }
        return out
    }

    private fun along(g: Guidance, p: LatLon) = g.update(p).progressM

    private fun turnReport(r: Route): String {
        val g = Guidance(r.path, emptyList())
        val ours = appTurns(g)
        // Real turns: left/right (and sharp); slight bends are optional.
        val ref = r.steps.filter { it.type != "depart" && it.type != "arrive" }
            .map { Triple(along(g, it.at), it.modifier, it.type) }
        val must = ref.filter { it.second in setOf("left", "right", "sharp left", "sharp right") }
        val missed = must.filter { (at, mod) ->
            val want = if ("left" in mod) Turn.LEFT else Turn.RIGHT
            ours.none { abs(it.first - at) <= 20 && it.second == want }
        }
        val wrongSide = must.filter { (at, mod) ->
            val want = if ("left" in mod) Turn.LEFT else Turn.RIGHT
            ours.any { abs(it.first - at) <= 20 && it.second != want } && ours.none { abs(it.first - at) <= 20 && it.second == want }
        }
        val extra = ours.filter { (at, _) -> ref.none { abs(it.first - at) <= 20 } }
        return "${r.name}: ${r.distance.toInt()}m, 실제 꺾는 곳 ${must.size} (살짝 ${ref.size - must.size}), 앱 안내 ${ours.size} | " +
            "빠짐 ${missed.size}${missed.joinToString("") { " [${it.first.toInt()}m ${it.second}]" }} | " +
            "반대 ${wrongSide.size} | 없는 데서 안내 ${extra.size}${extra.joinToString("") { " [${it.first.toInt()}m ${it.second}]" }}"
    }

    private data class Fix(val p: LatLon, val accuracy: Float, val t: Long, val truthM: Double)

    /** Meters east/north moved from [p]. */
    private fun shift(p: LatLon, east: Double, north: Double) =
        LatLon(p.lat + north / 111_320.0, p.lon + east / (111_320.0 * cos(Math.toRadians(p.lat))))

    private fun gauss(rnd: Random): Double {
        val u = 1.0 - rnd.nextDouble()
        val v = rnd.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u)) * cos(2 * Math.PI * v)
    }

    /** A walk along the route at [speed] m/s, one fix a second, with GPS noise and some jumps. */
    private fun walk(g: Guidance, rnd: Random, speed: Double = 1.3, noise: Double = 6.0, jumpEvery: Int = 40): List<Fix> {
        val out = mutableListOf<Fix>()
        var m = 0.0
        var t = 0L
        while (m < g.totalM + 5) {
            val truth = g.atProgress(m).snapped
            val jump = rnd.nextInt(jumpEvery) == 0
            val n = if (jump) 35.0 else noise
            val a = rnd.nextDouble() * 2 * Math.PI
            val r = abs(gauss(rnd)) * n
            out += Fix(shift(truth, r * cos(a), r * sin(a)), if (jump) 45f else (6 + rnd.nextDouble() * 8).toFloat(), t, m)
            m += speed
            t += 1000
        }
        return out
    }

    private class Result {
        var maxSkipM = 0.0
        var lagM = 0.0
        var offEpisodes = 0
        var vehicleFalse = 0
        var arrived = false
        var headingFlips = 0
        var backwardShown = 0
    }

    /** The guidance screen's steps for each fix. */
    private fun replay(g: Guidance, fixes: List<Fix>): Result {
        val res = Result()
        val filter = PositionFilter()
        val motion = MotionTracker()
        var furthest = 0.0
        var lastShown = 0.0
        var offSince: Long? = null
        var countedOff = false
        for (f in fixes) {
            val accepted = filter.accept(f.p, f.accuracy, f.t) ?: continue
            motion.add(accepted, f.t)
            if (motion.inVehicle) res.vehicleFalse++
            val raw = g.update(accepted, nearM = furthest)
            val s = if (raw.progressM < furthest && furthest - raw.progressM < 25.0 && raw.offRouteM <= offM) {
                g.atProgress(furthest).copy(offRouteM = raw.offRouteM)
            } else raw
            if (s.progressM - lastShown > res.maxSkipM) res.maxSkipM = s.progressM - lastShown
            if (s.progressM < lastShown - 1) res.backwardShown++
            res.lagM = maxOf(res.lagM, f.truthM - s.progressM)
            lastShown = s.progressM
            furthest = s.progressM
            if (s.arrived) res.arrived = true
            // Off the route for 8 s or more: the screen would look for a new route.
            if (s.offRouteM > offM) {
                val since = offSince ?: f.t.also { offSince = it }
                if (f.t - since >= 8_000 && !countedOff) { res.offEpisodes++; countedOff = true }
            } else { offSince = null; countedOff = false }
            val moving = (motion.speedMps ?: 0.0) >= 0.6
            val heading = guideHeading(s.offRouteM <= offM, moving, g.travelBearing(s.progressM), motion.courseDeg, null)
            val truthBearing = g.travelBearing(f.truthM)
            if (moving && com.deungbul.anshimgil.location.angleBetween(heading, truthBearing) > 100) res.headingFlips++
        }
        return res
    }

    /** Fixes from [from] going [bearing] degrees at [speed] m/s for [seconds], starting at [t0]. */
    private fun straight(from: LatLon, bearing: Double, speed: Double, seconds: Int, t0: Long, rnd: Random, noise: Double = 6.0): List<Fix> =
        (1..seconds).map { i ->
            val d = speed * i
            val p = shift(from, d * sin(Math.toRadians(bearing)), d * cos(Math.toRadians(bearing)))
            val a = rnd.nextDouble() * 2 * Math.PI
            val r = abs(gauss(rnd)) * noise
            Fix(shift(p, r * cos(a), r * sin(a)), 8f, t0 + i * 1000L, -1.0)
        }

    @Test
    fun situations() {
        assumeTrue("replay.dir 없음", dir?.isDirectory == true)
        val out = StringBuilder("== 상황 재현 (구간마다 10번)\n")
        dir!!.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map(::load).forEach { r ->
            val g = Guidance(r.path, emptyList())
            val wrong = mutableListOf<Long>(); val busOn = mutableListOf<Long>(); val busOff = mutableListOf<Long>()
            var busOffFalse = 0; var waitDrift = 0.0; var waitBack = 0.0
            for (seed in 1..10) {
                val rnd = Random(seed)
                val start = walk(g, rnd).takeWhile { it.truthM < g.totalM / 2 }
                val last = start.last()
                val here = g.atProgress(last.truthM)
                val routeBearing = g.travelBearing(last.truthM).toDouble()
                // 1) A wrong turn: off at a right angle, walking on.
                run {
                    val fixes = start + straight(here.snapped, routeBearing + 90, 1.3, 120, last.t, rnd)
                    val filter = PositionFilter(); var furthest = 0.0; var offSince: Long? = null; var hit: Long? = null
                    for (f in fixes) {
                        val a = filter.accept(f.p, f.accuracy, f.t) ?: continue
                        val raw = g.update(a, nearM = furthest)
                        val st = if (raw.progressM < furthest && furthest - raw.progressM < 25.0 && raw.offRouteM <= offM) g.atProgress(furthest).copy(offRouteM = raw.offRouteM) else raw
                        furthest = st.progressM
                        if (f.truthM < 0 && st.offRouteM > offM) { val s0 = offSince ?: f.t.also { offSince = it }; if (f.t - s0 >= 8_000 && hit == null) hit = f.t - last.t }
                        else if (st.offRouteM <= offM) offSince = null
                    }
                    hit?.let { wrong += it / 1000 }
                }
                // 2) A bus: 90 s at 9 m/s along the road, then off and walking again.
                run {
                    val bus = straight(here.snapped, routeBearing, 9.0, 90, last.t, rnd, noise = 8.0)
                    val walkAgain = straight(bus.last().p, routeBearing + 30, 1.2, 60, bus.last().t, rnd)
                    val motion = MotionTracker(); val filter = PositionFilter(); var on: Long? = null; var off: Long? = null
                    for (f in start + bus + walkAgain) {
                        val a = filter.accept(f.p, f.accuracy, f.t) ?: continue
                        val was = motion.inVehicle
                        motion.add(a, f.t)
                        if (!was && motion.inVehicle && on == null) on = (f.t - last.t) / 1000
                        if (was && !motion.inVehicle) off = (f.t - bus.last().t) / 1000
                    }
                    on?.let { busOn += it }; off?.let { busOff += it } ?: busOffFalse++
                }
                // 3) Waiting 45 s at a crossing (standing, noisy GPS), then walking on.
                run {
                    val wait = (1..45).map { i -> val a = rnd.nextDouble() * 2 * Math.PI; val rr = abs(gauss(rnd)) * 8; Fix(shift(here.snapped, rr * cos(a), rr * sin(a)), 10f, last.t + i * 1000L, last.truthM) }
                    val filter = PositionFilter(); val motion = MotionTracker(); var furthest = 0.0; var before = 0.0
                    for (f in start + wait) {
                        val a = filter.accept(f.p, f.accuracy, f.t) ?: continue
                        motion.add(a, f.t)
                        val raw = g.update(a, nearM = furthest)
                        val st = if (raw.progressM < furthest && furthest - raw.progressM < 25.0 && raw.offRouteM <= offM) g.atProgress(furthest) else raw
                        if (f.t <= last.t) before = st.progressM else {
                            waitDrift = maxOf(waitDrift, st.progressM - last.truthM)
                            waitBack = maxOf(waitBack, before - st.progressM)
                        }
                        furthest = st.progressM
                    }
                }
            }
            out.appendLine(
                "${r.name}: 길 잘못 듦 → 새 길 찾기 ${wrong.size}/10 (${wrong.minOrNull()}~${wrong.maxOrNull()}초 뒤) | " +
                    "버스 → 차로 앎 ${busOn.size}/10 (${busOn.minOrNull()}~${busOn.maxOrNull()}초 뒤), 내려 걸으면 풀림 ${busOff.size}/10 (${busOff.minOrNull()}~${busOff.maxOrNull()}초 뒤) | " +
                    "45초 서 있기: 앞으로 밀림 최대 ${waitDrift.toInt()}m, 뒤로 감 최대 ${waitBack.toInt()}m",
            )
        }
        File("build/guide-situations.txt").writeText(out.toString())
    }

    @Test
    fun replayRealRoutes() {
        assumeTrue("replay.dir 없음", dir?.isDirectory == true)
        val routes = dir!!.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map(::load)
        val report = StringBuilder()
        report.appendLine("== 꺾는 곳 안내: 앱 vs OpenStreetMap 도보 경로 안내")
        routes.forEach { report.appendLine(turnReport(it)) }
        report.appendLine()
        report.appendLine("== 보통 걷기 재현 (구간마다 10번, GPS 흔들림 6m, 40초에 한 번쯤 35m 튐)")
        routes.forEach { r ->
            val g = Guidance(r.path, emptyList())
            val runs = (1..10).map { seed -> replay(g, walk(g, Random(seed))) }
            report.appendLine(
                "${r.name}: 도착 ${runs.count { it.arrived }}/10, 길 벗어남 오판 ${runs.sumOf { it.offEpisodes }}번, " +
                    "차 오판 ${runs.count { it.vehicleFalse > 0 }}/10, 앞으로 건너뜀 최대 ${runs.maxOf { it.maxSkipM }.toInt()}m, " +
                    "실제보다 늦음 최대 ${runs.maxOf { it.lagM }.toInt()}m, 뒤로 감 ${runs.sumOf { it.backwardShown }}번, 화살표 뒤집힘 ${runs.sumOf { it.headingFlips }}번",
            )
        }
        File("build/guide-replay.txt").writeText(report.toString())
        println(report)
    }
}
