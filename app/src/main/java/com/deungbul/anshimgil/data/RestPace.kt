package com.deungbul.anshimgil.data

/*
 * 지치면 쉬는 곳으로 (팀 아이디어 5-1, 워치 없이): while guiding a senior or a wheelchair user, the walk along the
 * route is watched; when it has gone much slower than the walker's own pace for a minute and a half, AI 비서
 * offers the nearest shelter, toilet or shop to rest at, guides there first, and goes on after [다시 출발].
 * With a watch later, the heart rate could join in; for now the pace on the route is the sign.
 */

/** Slower than this share of the walker's own pace counts as slowing down. */
const val SLOW_SHARE = 0.6

/** …over this long (a wait at a crossing is shorter). */
const val SLOW_WINDOW_MS = 90_000L

/** Hardly moving at all is a stop (resting already, or a long red light), not slowing down. */
const val STILL_MPS = 0.05

/** Not asked again for this long after an offer. */
const val REST_ASK_AGAIN_MS = 10 * 60_000L

/** A rest place this far from the walker at most (straight line). */
const val REST_NEAR_M = 200

/** Not asked this close to the destination. */
const val REST_NOT_NEAR_END_M = 150

/**
 * Places to rest at, best first: the team's 쉼 곳 (route-weights.md 4장 `rest`): 무더위·한파쉼터 and public toilets,
 * only those open now (the route's places come from the server, which keeps only those open now, `Availability`),
 * and no toilets at night ("밤에는 화장실을 경로에 넣지 않는다"). The app's convenience store (11/9) was dropped.
 */
val REST_KINDS = listOf(
    FacilityKind.HEAT_SHELTER, FacilityKind.COLD_SHELTER, FacilityKind.ACCESSIBLE_TOILET, FacilityKind.TOILET,
)

/** The rest kinds at [night]: shelters only. */
fun restKinds(night: Boolean): List<FacilityKind> =
    if (night) REST_KINDS.filter { it != FacilityKind.TOILET && it != FacilityKind.ACCESSIBLE_TOILET } else REST_KINDS

/** Who is asked: those for whom tiring out matters most (team idea: 어르신·장애인). */
fun asksToRest(mode: UserMode) = mode == UserMode.SENIOR || mode == UserMode.DISABLED

/** Watches progress along the route over time; [slow] when the last [SLOW_WINDOW_MS] went much slower than usual. */
class PaceWatch(private val usualMps: Double, private val windowMs: Long = SLOW_WINDOW_MS) {
    private val times = ArrayDeque<Long>()
    private val progress = ArrayDeque<Double>()

    fun add(progressM: Double, timeMs: Long) {
        times.addLast(timeMs)
        progress.addLast(progressM)
        // Keep a little more than the window, so a full window is always there to measure.
        while (times.size > 2 && timeMs - times[1] >= windowMs) {
            times.removeFirst()
            progress.removeFirst()
        }
    }

    fun reset() {
        times.clear()
        progress.clear()
    }

    /** Walking speed over the window (m/s); null until the window is full. */
    val paceMps: Double?
        get() {
            if (times.size < 2) return null
            val span = times.last() - times.first()
            if (span < windowMs) return null
            return (progress.last() - progress.first()).coerceAtLeast(0.0) / (span / 1000.0)
        }

    val slow: Boolean
        get() = paceMps?.let { it > STILL_MPS && it < usualMps * SLOW_SHARE } == true
}

/** A place to rest and how far it is. */
/** A place to rest, how far it is, and whether it lies [ahead] on the way (not back the way the walker came). */
data class RestPlace(val facility: Facility, val meters: Int, val ahead: Boolean = false)

/** A rest place ahead counts when it is this far along the route at most… */
const val REST_AHEAD_M = 300

/** …and this close to the route. */
const val REST_OFF_ROUTE_M = 50

/** A place this far behind still counts as here (GPS wobble). */
private const val REST_BEHIND_M = 10

/**
 * The place to rest at: the best kind lying ahead on the way ([onRoute] gives a place's meters along the route and
 * meters off it), nearest first. A tired walker is never sent back the way they came or round a detour (11/9, 11/10):
 * with nothing ahead there is no offer.
 */
fun restPlaceOnTheWay(
    facilities: List<Facility>,
    here: LatLon,
    progressM: Double,
    night: Boolean = false,
    onRoute: (Facility) -> Pair<Double, Double>,
): RestPlace? {
    val kinds = restKinds(night)
    val ahead = facilities.filter { it.kind in kinds && it.look == FacilityLook.NORMAL }.mapNotNull { f ->
        val (along, off) = onRoute(f)
        val toGo = along - progressM
        if (toGo < -REST_BEHIND_M || toGo > REST_AHEAD_M || off > REST_OFF_ROUTE_M) null
        else RestPlace(f, (toGo.coerceAtLeast(0.0) + off).toInt(), ahead = true)
    }.minWithOrNull(compareBy<RestPlace> { REST_KINDS.indexOf(it.facility.kind) }.thenBy { it.meters })
    return ahead
}

/** Within this many meters before the rest place's spot on the route, the walker is there. */
const val REST_REACHED_M = 15.0

/** Reached the rest place: walked up to [restAlongM], its spot on the route. */
fun restReachedAt(progressM: Double, restAlongM: Double): Boolean = progressM >= restAlongM - REST_REACHED_M

/** The best place to rest near [here]: the best kind within [REST_NEAR_M], the nearer one among the same kind. */
fun restPlace(facilities: List<Facility>, here: LatLon): RestPlace? =
    facilities.filter { it.kind in REST_KINDS }
        .map { RestPlace(it, distanceMeters(here.lat, here.lon, it.lat, it.lon)) }
        .filter { it.meters <= REST_NEAR_M }
        .minWithOrNull(compareBy<RestPlace> { REST_KINDS.indexOf(it.facility.kind) }.thenBy { it.meters })

/** The place's name for the walker: its own name, or the kind ("무더위쉼터"). */
fun restName(p: RestPlace): String = p.facility.name?.takeIf { it.isNotBlank() } ?: p.facility.kind.label

/** AI 비서's offer: "걸음이 조금 느려지셨어요. 가는 길 80m 앞 무더위쉼터에서 쉬었다 가실래요?" (or nearby, turning back). */
fun restOfferText(p: RestPlace): String =
    if (p.ahead) "걸음이 조금 느려지셨어요. 가는 길 ${p.meters}m 앞 ${restName(p)}에서 쉬었다 가실래요?"
    else "걸음이 조금 느려지셨어요. ${p.meters}m 거리 ${restName(p)}에서 쉬었다 가실래요? 조금 돌아가야 해요."

/** Said on reaching it. */
fun restArrivedText(p: RestPlace): String = "${restName(p)}에 왔어요. 충분히 쉬시고, 다 쉬셨으면 [다시 출발]을 눌러 주세요."

/*
 * The demo walk (가상 이동, when the phone is far from the route) slows down for a while in the senior and wheelchair
 * modes, like a walker tiring, so the rest offer can be shown in a demonstration. Its own pace is the "usual" one and
 * the window is shorter, so the offer comes after about twenty seconds of slowing rather than a minute and a half.
 */

/** The demo walk slows down from this second… */
const val DEMO_SLOW_FROM_S = 20

/** …for this long… */
const val DEMO_SLOW_FOR_S = 45

/** …to this share of its pace. */
const val DEMO_SLOW_SHARE = 0.35

/** The demo walk's window for slowing down. */
const val DEMO_SLOW_WINDOW_MS = 20_000L

/** Meters the demo walk goes in second [second] of the walk; [slows]: this walk slows down once (see above). */
fun demoStepM(second: Int, normalM: Double, slows: Boolean): Double =
    if (slows && second >= DEMO_SLOW_FROM_S && second < DEMO_SLOW_FROM_S + DEMO_SLOW_FOR_S) normalM * DEMO_SLOW_SHARE else normalM
