package com.deungbul.anshimgil.ui.guide

import com.deungbul.anshimgil.data.DEMO_SLOW_WINDOW_MS
import com.deungbul.anshimgil.data.demoStepM
import com.deungbul.anshimgil.map.WALK_TILT_DEGREES
import com.deungbul.anshimgil.data.MovingState
import com.kakao.vectormap.camera.CameraPosition
import com.deungbul.anshimgil.data.RunDetector
import com.deungbul.anshimgil.data.runAlert
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.deungbul.anshimgil.data.restArrivedText
import com.deungbul.anshimgil.data.restOfferText
import com.deungbul.anshimgil.data.restName
import com.deungbul.anshimgil.data.restPlaceOnTheWay
import com.deungbul.anshimgil.data.restReachedAt
import com.deungbul.anshimgil.data.asksToRest
import com.deungbul.anshimgil.data.REST_NOT_NEAR_END_M
import com.deungbul.anshimgil.data.REST_ASK_AGAIN_MS
import com.deungbul.anshimgil.data.RestPlace
import com.deungbul.anshimgil.data.PaceWatch
import androidx.compose.runtime.SideEffect
import com.deungbul.anshimgil.ui.common.AI_BUBBLE_ALPHA
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import com.deungbul.anshimgil.data.eventAheadText
import com.deungbul.anshimgil.data.eventsAhead
import com.deungbul.anshimgil.data.EVENT_CHECK_MS
import com.deungbul.anshimgil.data.EventRepository
import com.deungbul.anshimgil.data.EventAhead
import androidx.compose.runtime.rememberUpdatedState
import com.deungbul.anshimgil.data.sidewalkHint
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deungbul.anshimgil.data.walkMinutes
import com.deungbul.anshimgil.data.personalSpeed
import com.deungbul.anshimgil.data.WalkMeter
import com.deungbul.anshimgil.data.WalkSpeedRepository
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import com.deungbul.anshimgil.data.HelpAhead
import com.deungbul.anshimgil.data.RouteRequest
import com.deungbul.anshimgil.data.StopPlace
import com.deungbul.anshimgil.data.stopArrivedText
import com.deungbul.anshimgil.data.stopReached
import com.deungbul.anshimgil.data.stopsLeftText
import com.deungbul.anshimgil.map.addStopPins
import com.deungbul.anshimgil.data.RouteRepository
import androidx.compose.material3.TextButton
import com.deungbul.anshimgil.ui.safety.sendGuardianText
import com.deungbul.anshimgil.ui.safety.SosOverlay
import com.deungbul.anshimgil.ui.safety.SosButton
import com.deungbul.anshimgil.data.guardianMessage
import com.deungbul.anshimgil.data.GuardianAlert
import com.deungbul.anshimgil.data.Guardian
import com.kakao.vectormap.camera.CameraAnimation
import androidx.compose.runtime.mutableFloatStateOf
import com.deungbul.anshimgil.data.FacilityRepository
import com.deungbul.anshimgil.data.ApiException
import com.deungbul.anshimgil.data.bboxAround
import com.deungbul.anshimgil.data.REPORT_NEAR_ROUTE_M
import com.deungbul.anshimgil.data.ReportSpot
import com.deungbul.anshimgil.data.RoadReportRepository
import com.deungbul.anshimgil.data.reportAheadText
import com.deungbul.anshimgil.data.reportSpotTitle
import com.deungbul.anshimgil.data.reportSpotsAhead
import com.deungbul.anshimgil.map.showReportSpots
import com.deungbul.anshimgil.map.addTurnArrows
import com.deungbul.anshimgil.map.addCrosswalks
import com.deungbul.anshimgil.location.angleBetween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.PaddingValues
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import com.deungbul.anshimgil.location.LOCATION_PERMISSIONS
import com.deungbul.anshimgil.location.LocationUpdates
import com.deungbul.anshimgil.ui.route.arrivalTime
import com.deungbul.anshimgil.location.hasLocationPermission
import com.deungbul.anshimgil.data.Facility
import com.deungbul.anshimgil.data.Guidance
import com.deungbul.anshimgil.data.GuidanceState
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.arrowHeading
import com.deungbul.anshimgil.data.OffRouteCounter
import com.deungbul.anshimgil.data.atCrossing
import com.deungbul.anshimgil.data.offRouteLimitM
import com.deungbul.anshimgil.data.guideHeading
import com.deungbul.anshimgil.data.MotionTracker
import com.deungbul.anshimgil.ui.mode.ModeBadge
import com.deungbul.anshimgil.data.RouteResult
import com.deungbul.anshimgil.data.RouteType
import com.deungbul.anshimgil.data.Turn
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.map.KakaoMapView
import com.deungbul.anshimgil.ui.home.PlaceDetailCard
import com.deungbul.anshimgil.map.addMyLocation
import com.deungbul.anshimgil.map.pointTo
import com.deungbul.anshimgil.map.tiltForWalking
import com.deungbul.anshimgil.location.rememberCompassHeading
import com.deungbul.anshimgil.map.showRoutes
import com.deungbul.anshimgil.map.toLatLng
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF4A564E)
private val Green = Color(0xFF1F6B42)

/** A real fix farther than this from the route switches to the demo walk (e.g. testing away from the route). */
private const val FAR_FROM_ROUTE_M = 300
// 11/8: 40 → 25. Replaying real routes with a made-up GPS, a wrong turn took 40–60 s to notice; at 25 m it takes
// about 30 s, and a normal walk (6 m noise, jumps) was never taken for leaving the route.
private const val OFF_ROUTE_WARN_M = 25
/** Going back along the route less than this is treated as GPS noise. */
private const val BACKTRACK_M = 25.0
/** Off the route this long before finding a new route from where the walker is. */
private const val REROUTE_AFTER_MS = 8_000L

/** How long AI 비서's short notice stays under the status line. */
private const val AI_NOTICE_MS = 10_000L
/** On the route this long after a new route, and its card closes by itself (m25). */
private const val CARD_CLOSE_ON_ROUTE_MS = 15_000L
/** Off the route this long before offering to tell the guardians. */
private const val OFF_ROUTE_ALERT_MS = 20_000L
private const val DEMO_STEP_M = 8.0
/** Where the walker sits in the map below the instruction card: this share of the way down. */
private const val WALKER_DOWN = 0.7

/** Top padding that puts the map's center [WALKER_DOWN] of the way down the part below [coveredPx]. */
internal fun walkerTopPaddingPx(coveredPx: Int, mapHeightPx: Int): Int {
    if (mapHeightPx <= coveredPx) return coveredPx
    val y = coveredPx + WALKER_DOWN * (mapHeightPx - coveredPx)
    return (2 * y - mapHeightPx).toInt().coerceIn(coveredPx, mapHeightPx - 1)
}

/** Reports along the route are read again this often. */
private const val REPORTS_AGAIN_MS = 5 * 60_000L

/** Close-up for walking: the next few buildings, with the map tilted so the street ahead shows. */
private const val GUIDE_ZOOM = 19

/** How long a tapped picture's card stays before closing by itself. */
private const val TAPPED_CARD_MS = 15_000L
/** A crosswalk this close to the route line is one the route crosses. */
private const val CROSSWALK_ON_ROUTE_M = 8.0
/** Position updates come about once a second; moving over slightly less keeps the marker gliding. */
private const val FOLLOW_MS = 900
private val FOLLOW = CameraAnimation.from(FOLLOW_MS, false, true)

/** A turn of the map is shown this fast, so it keeps up with the walker turning. */
private val TURN = CameraAnimation.from(ARROW_TURN_MS, false, true)
/** The map turns only when the walker turns by at least this much. */
private const val MAP_TURN_DEGREES = 15f

/** Standing still the map follows a turn of the body from this many degrees. */
private const val MAP_TURN_STILL_DEGREES = 8f

/** The arrow turns quicker than the map, so a turn of the body shows right away. */
private const val ARROW_TURN_MS = 250



/**
 * Walking guidance along the chosen route (issue #7): next turn, nearest help, remaining distance,
 * optional voice. Uses GPS when it is on the route; otherwise walks the route as a demo.
 * Location is only used on the phone and never sent or logged (NFR-07).
 */
@Composable
fun GuideScreen(
    mode: UserMode,
    result: RouteResult,
    type: RouteType,
    destinationName: String,
    voiceByDefault: Boolean,
    facilityRepository: FacilityRepository,
    guardians: List<Guardian>,
    showTurnSigns: Boolean,
    showSos: Boolean,
    routeRepository: RouteRepository,
    walkSpeedRepository: WalkSpeedRepository,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    /** The finish button's words when this walk is one part of a longer trip (대중교통 안내), e.g. "다음: 21번 타기". */
    finishLabel: String? = null,
    /** AI 비서's microphone on the map; it opens over the guidance, which keeps running. */
    onOpenAssistant: (() -> Unit)? = null,
    /** 불편 신고 over the guidance (it keeps running underneath); null hides the button. */
    onOpenReport: (() -> Unit)? = null,
    /** Goes up by one each time a report was sent during this guidance: then "가시던 곳으로 다시 찾을까요?". */
    reportedCount: Int = 0,
    /** Whether the walker has reached the destination (for the trip record). */
    onArrivedChange: (Boolean) -> Unit = {},
    /** AI 비서's live words here and the walker's answers, for the chat (fromUser, text). */
    onLive: (Boolean, String) -> Unit = { _, _ -> },
) {
    var sos by remember { mutableStateOf(false) }
    var showHelpList by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // The route being followed; replaced by a new one from where the walker is when they leave it.
    var active by remember { mutableStateOf(result) }
    val route = active.route(type) ?: active.routes.first()
    val guidance = remember(route) { Guidance(route.path, active.facilitiesOn(route.type), route.crossings) }
    val destination = remember { (result.route(type) ?: result.routes.first()).path.last() }
    // 들를 곳 (11-09): the stops as first planned keep their numbers; [stopsDone] holds those seen or skipped.
    val plannedStops = remember { (result.route(type) ?: result.routes.first()).stops }
    var stopsDone by remember { mutableStateOf(setOf<Int>()) }
    var atStop by remember { mutableStateOf<Int?>(null) }
    var fix by remember { mutableStateOf<LatLon?>(null) }
    var demoProgress by remember { mutableDoubleStateOf(0.0) }
    var voiceOn by rememberSaveable { mutableStateOf(voiceByDefault) }
    var map by remember { mutableStateOf<KakaoMap?>(null) }
    // A picture on the map the walker tapped, told in a card.
    var tapped by remember { mutableStateOf<Facility?>(null) }
    var me by remember { mutableStateOf<Label?>(null) }
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = hasLocationPermission(context)
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }
    // Personal walking speed: measured on this walk from real positions, saved when the screen closes.
    val samples by remember(mode) { walkSpeedRepository.samples(mode) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val speed = personalSpeed(mode, samples)
    val meter = remember { WalkMeter() }
    DisposableEffect(Unit) {
        onDispose {
            meter.sample(java.time.LocalDate.now().toString())?.let { sample ->
                // The screen is gone, so this outlives it; the write is quick and local.
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { walkSpeedRepository.add(mode, sample) }
            }
        }
    }
    // Speed and course from the fixes; a car or bus pauses walking guidance.
    val motion = remember { MotionTracker() }
    var inVehicle by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val movingState = remember { MovingState() }
    var course by remember { mutableStateOf<Float?>(null) }
    // 뛰기 경고 (어린이 모드): running told from the phone's shaking, backed by GPS speed.
    val runDetector = remember { RunDetector() }
    var running by remember { mutableStateOf(false) }
    if (mode == UserMode.CHILD) DisposableEffect(Unit) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                runDetector.addAccel(e.values[0], e.values[1], e.values[2], e.timestamp / 1_000_000)
                running = runDetector.running
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accel != null) sensors.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensors?.unregisterListener(listener) }
    }
    if (hasPermission) LocationUpdates(context) {
        val now = android.os.SystemClock.elapsedRealtime()
        motion.add(it, now)
        runDetector.addSpeed(motion.speedMps)
        running = runDetector.running
        inVehicle = motion.inVehicle
        moving = movingState.update(motion.speedMps, now)
        course = motion.courseDeg
        fix = it
        // A ride is not a walk: it would make the personal walking speed far too fast.
        if (!motion.inVehicle) meter.add(it, now)
    }

    // Along the route the walker only goes forward: a fix a few meters behind the last one is GPS noise,
    // so the marker stays put instead of sliding back and forth. A real step back (U-turn) still shows.
    var furthest by remember(guidance) { mutableDoubleStateOf(0.0) }
    val realState = fix?.let { guidance.update(it, nearM = furthest) }?.let { raw ->
        if (raw.progressM < furthest && furthest - raw.progressM < BACKTRACK_M && raw.offRouteM <= OFF_ROUTE_WARN_M) {
            guidance.atProgress(furthest).copy(offRouteM = raw.offRouteM)
        } else {
            raw
        }
    }
    LaunchedEffect(realState?.progressM) { realState?.let { furthest = it.progressM } }
    val demo = realState == null || realState.offRouteM > FAR_FROM_ROUTE_M
    val state: GuidanceState = if (demo) guidance.atProgress(demoProgress) else realState!!

    // 지치면 쉬는 곳으로: [resting] is the place ahead on the route to rest at. The walker keeps to the same route
    // (no new route, so no detour or turning back; 11/10) and rests on reaching its spot on the route.
    var resting by remember { mutableStateOf<RestPlace?>(null) }
    // Rested and set off again from there.
    var restReached by remember { mutableStateOf(false) }
    val restAlongM = remember(guidance, resting) { resting?.let { guidance.alongAndOff(LatLon(it.facility.lat, it.facility.lon)).first } }
    val atRest = resting != null && (state.arrived || restAlongM?.let { restReachedAt(state.progressM, it) } == true)

    // Demo walk: move along the route a few meters per second; in the senior and wheelchair modes it slows down
    // once for a while, like a walker tiring, so the rest offer shows in a demonstration.
    // The route changes on the way (a rest stop, setting off again, finding the way again), so the loop reads
    // the current one: with the first route's length it stopped for good after a rest (영상 11/9, 925m에서 멈춤).
    // 들를 곳 not yet seen, and where each lies along the route.
    val leftStops = plannedStops.indices.filter { it !in stopsDone }
    val leftAlong = remember(guidance, stopsDone) { guidance.alongInOrder(leftStops.map { plannedStops[it].point }) }
    val currentGuidance by rememberUpdatedState(guidance)
    // On the way to a rest the demo walker stops there until [다시 출발].
    val currentRestStop by rememberUpdatedState(restAlongM?.takeIf { !restReached })
    // …and at the next 들를 곳 until [다 봤어요] (it walked on past it; 11/10 phone check).
    val currentNextStop by rememberUpdatedState(leftAlong.firstOrNull())
    LaunchedEffect(demo) {
        var second = 0
        while (demo) {
            delay(1000)
            val end = listOfNotNull(currentRestStop, currentNextStop, currentGuidance.totalM).min()
            if (demoProgress < end) demoProgress = (demoProgress + demoStepM(second++, DEMO_STEP_M, slows = asksToRest(mode))).coerceAtMost(end)
        }
    }

    LaunchedEffect(state.arrived, resting) { onArrivedChange(state.arrived && resting == null) }

    val tts = rememberTts(context)
    val instruction = instructionText(state)
    val spokenKey = "${state.nextTurn}-${distanceBucket(state.metersToNextTurn)}-${state.arrived}"
    LaunchedEffect(spokenKey, voiceOn) {
        // At the rest place the rest card speaks instead of "목적지에 도착했어요".
        if (resting != null && state.arrived) return@LaunchedEffect
        if (voiceOn && !inVehicle) tts.speak(instruction.spoken, TextToSpeech.QUEUE_FLUSH, null, "guide")
    }
    // A running child near a crossing or where roads meet: vibrate, beep and say it, once for each place.
    val runWarned = remember(guidance) { mutableSetOf<String>() }
    // The warning on the screen too, for a few seconds (the buzz alone was easy to miss; 11/9 emulator check).
    var runShown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(runShown) { if (runShown != null) { delay(RUN_SHOWN_MS); runShown = null } }
    val runNow = if (mode == UserMode.CHILD && running && !inVehicle && !state.arrived) {
        runAlert(state.progressM, guidance.crossingsAlongM, state.nextTurn, state.metersToNextTurn, runWarned)
    } else {
        null
    }
    LaunchedEffect(runNow?.key) {
        val alert = runNow ?: return@LaunchedEffect
        runWarned += alert.key
        runShown = alert.text
        warnRunning(context)
        onLive(false, alert.text)
        if (voiceOn) tts.speak(alert.text, TextToSpeech.QUEUE_FLUSH, null, "run")
    }
    LaunchedEffect(inVehicle) {
        if (inVehicle) onLive(false, "차로 이동하는 것 같아요. 내려서 걸으면 다시 안내할게요.")
        if (inVehicle && voiceOn) tts.speak("차로 이동하는 것 같아요. 내려서 걸으면 다시 안내할게요.", TextToSpeech.QUEUE_FLUSH, null, "vehicle")
    }

    // A tapped 신고 쌓인 곳 says what it is in the notice line.
    var tappedReport by remember { mutableStateOf<ReportSpot?>(null) }
    // Drawn again whenever the route changes (after finding a new one).
    LaunchedEffect(map, route) {
        val m = map ?: return@LaunchedEffect
        // A tapped picture on the way says what it is (11/9).
        m.setOnLabelClickListener { _, _, label ->
            (label.tag as? Facility)?.let { tapped = it }
            (label.tag as? ReportSpot)?.let { tappedReport = it }
            true
        }
        // Stop pins are drawn here with their first numbers, not the new route's.
        m.showRoutes(context, active.copy(routes = listOf(route.copy(stops = emptyList()))), route.type, destinationName, walking = true)
        m.addStopPins(context, plannedStops.withIndex().filter { it.index !in stopsDone }.map { it.index + 1 to it.value.point })
        if (showTurnSigns) m.addTurnArrows(context, guidance.turnMarks)
        me = m.addMyLocation(context, state.snapped, sizeDp = 24f)
        m.moveCamera(CameraUpdateFactory.newCenterPosition(state.snapped.toLatLng(), GUIDE_ZOOM))
        m.tiltForWalking()
        // Crosswalks the route goes over: the map's crosswalks lying on the route line.
        val crossings = try {
            facilityRepository.crosswalks(bboxAround(route.path)).filter { guidance.distanceToRoute(it) <= CROSSWALK_ON_ROUTE_M }
        } catch (e: ApiException) {
            emptyList()
        }
        m.addCrosswalks(context, crossings)
    }

    // The way the walker faces. Walking on the route: the route ahead, which cannot flip round as the
    // compass did when the phone was held upright (11/8). Off the route: the GPS course. Standing still:
    // the compass. The demo walk follows the route.
    val compass = rememberCompassHeading()
    val travel = guidance.travelBearing(state.progressM)
    val onRoute = !demo && state.offRouteM <= OFF_ROUTE_WARN_M
    val heading = if (demo) travel else guideHeading(onRoute, moving, travel, course, compass)

    // Heading-up, like Naver's walking guidance: the map turns so the way ahead points up. It turns only
    // for a real change of direction and slowly, so it does not swing about (11/8).
    val arrow = if (demo) heading else arrowHeading(moving, heading, compass)
    var mapBearing by remember { mutableFloatStateOf(heading) }
    // The map turns with the phone (compass), walking or not, so the arrow stays pointing up and the street
    // in front of the walker is the street at the top of the screen (11/10, heading-up like Naver).
    LaunchedEffect(arrow) {
        val step = if (demo) MAP_TURN_DEGREES else MAP_TURN_STILL_DEGREES
        if (angleBetween(mapBearing, arrow) >= step) mapBearing = arrow
    }
    // Moving and turning in one camera move: two moves sent apart cut each other off, so the map jerked at
    // every fix while it turned (11/9 video). Kakao turns the map the other way round from a compass bearing,
    // hence the minus.
    // A turn is shown quickly; a move alone glides over the time until the next fix.
    var shownBearing by remember { mutableFloatStateOf(mapBearing) }
    LaunchedEffect(map, state.snapped, mapBearing) {
        val turning = angleBetween(shownBearing, mapBearing) > 0.5f
        shownBearing = mapBearing
        // Glide to each new position over about the time until the next fix, instead of jumping.
        me?.moveTo(state.snapped.toLatLng(), FOLLOW_MS)
        val m = map ?: return@LaunchedEffect
        val now = m.cameraPosition
        m.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.from(
                    state.snapped.lat, state.snapped.lon, GUIDE_ZOOM,
                    now?.tiltAngle ?: Math.toRadians(WALK_TILT_DEGREES), Math.toRadians(((360 - mapBearing) % 360).toDouble()), now?.height ?: 0.0,
                ),
            ),
            if (turning) TURN else FOLLOW,
        )
    }
    // Marker rotation is measured on the screen, not the map, so take off the map's own turn; it turns over
    // the same time as the map, so the two stay together.
    LaunchedEffect(me, arrow, mapBearing) { me?.pointTo(arrow - mapBearing, ARROW_TURN_MS) }

    // 귀가 지킴이: off the planned route for a while, offer to tell the guardians (once per stretch off route).
    // In a car or bus the route is left on purpose: no off-route warning, no new route.
    // Stepping into the rest place (a little off the route) is not leaving the route.
    // At a turn or crossing the route line (middle of the road) is far from the pavement: allow more there, and
    // only two fixes in a row past it count, so one jumpy fix finds no new route (m25, 11/11).
    val offLimit = offRouteLimitM(atCrossing(state.snapped, guidance.turnMarks))
    val offCounter = remember(guidance) { OffRouteCounter() }
    var offInRow by remember(guidance) { mutableStateOf(false) }
    LaunchedEffect(fix, guidance) { if (fix != null) offInRow = offCounter.update(state.offRouteM, offLimit) }
    val off = !demo && !inVehicle && !atRest && offInRow
    var offRouteLong by remember { mutableStateOf(false) }
    var offRouteTold by remember { mutableStateOf(false) }
    LaunchedEffect(off) {
        if (off) {
            delay(OFF_ROUTE_ALERT_MS)
            offRouteLong = true
        } else {
            offRouteLong = false
            offRouteTold = false
        }
    }

    // Off the route for a few seconds: find a new route from here to the same destination, like a car
    // navigator. The guardians can then be told the walker left the planned route.
    var rerouting by remember { mutableStateOf(false) }
    var rerouted by remember { mutableStateOf(false) }
    /** A new route from [from] to the same destination; false when none was found (the old one stays). */
    suspend fun findNewRoute(from: LatLon, spoken: String): Boolean {
        rerouting = true
        val found = try {
            // A rest place on the way stays on the way: its spot is found again on the new route.
            val left = plannedStops.filterIndexed { i, _ -> i !in stopsDone }.map { StopPlace(it.name, it.point) }
            active = routeRepository.findRoutes(RouteRequest(from, destination, mode, stops = left))
            demoProgress = 0.0
            if (voiceOn) tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "reroute")
            true
        } catch (e: ApiException) {
            // Too close to the destination, out of the area, or no connection: keep the old route.
            false
        }
        rerouting = false
        return found
    }
    // The "새 길로 안내해요" card goes away once the walker is on the route again for a while (m25: it stayed
    // until a button was pressed, over 10 minutes).
    val backOnRoute = !demo && state.offRouteM <= offLimit
    LaunchedEffect(rerouted, backOnRoute) {
        if (rerouted && backOnRoute) {
            delay(CARD_CLOSE_ON_ROUTE_MS)
            rerouted = false
        }
    }
    // AI 비서's short word about what just happened (a new route found), shown under the status line for a while.
    var aiNotice by remember { mutableStateOf<String?>(null) }

    // 신고 쌓인 곳 along the route (11/10): drawn on the map, and told once each when coming up ahead.
    var reportSpotsOnRoute by remember { mutableStateOf<List<ReportSpot>>(emptyList()) }
    LaunchedEffect(route) {
        while (true) {
            runCatching { RoadReportRepository.activeWithin(bboxAround(route.path, marginDeg = 0.001)) }
                .onSuccess { all -> reportSpotsOnRoute = all.filter { guidance.alongAndOff(it.point).second <= REPORT_NEAR_ROUTE_M } }
            delay(REPORTS_AGAIN_MS)
        }
    }
    LaunchedEffect(map, reportSpotsOnRoute) { map?.showReportSpots(context, reportSpotsOnRoute) }
    LaunchedEffect(tappedReport) { tappedReport?.let { aiNotice = reportSpotTitle(it); tappedReport = null } }
    val toldReports = remember { mutableSetOf<Long>() }
    LaunchedEffect(state.progressM.toInt() / 10, reportSpotsOnRoute) {
        val (spot, aheadM) = reportSpotsAhead(reportSpotsOnRoute, state.progressM) { guidance.alongAndOff(it) }
            .firstOrNull { it.first.spotId !in toldReports } ?: return@LaunchedEffect
        toldReports += spot.spotId
        val said = reportAheadText(spot, aheadM)
        aiNotice = said
        onLive(false, said)
        if (voiceOn) tts.speak(said, TextToSpeech.QUEUE_FLUSH, null, "report")
    }
    LaunchedEffect(aiNotice) { aiNotice?.let { onLive(false, it) } }
    LaunchedEffect(aiNotice) {
        if (aiNotice != null) {
            delay(AI_NOTICE_MS)
            aiNotice = null
        }
    }
    LaunchedEffect(off) {
        if (!off) return@LaunchedEffect
        delay(REROUTE_AFTER_MS)
        val here = fix ?: return@LaunchedEffect
        if (findNewRoute(here, "길을 벗어나 새 길을 찾았어요")) {
            rerouted = true
            aiNotice = "길을 벗어나서 지금 자리에서 새 길을 찾았어요."
        }
    }

    // After a report (button or AI 비서), as with a road event: offer a new way to the same place (진행표, 11/10).
    var askRefind by remember { mutableStateOf(false) }
    val reportsAtStart = remember { reportedCount }
    LaunchedEffect(reportedCount) {
        if (reportedCount <= reportsAtStart) return@LaunchedEffect
        askRefind = true
        onLive(false, REFIND_QUESTION)
        if (voiceOn) tts.speak(REFIND_QUESTION, TextToSpeech.QUEUE_FLUSH, null, "refind")
    }

    // Roadworks, an accident or a closure that started after the route was found: ask the server every few
    // minutes and tell the walker about a new one on the road ahead. Events there at the start are left out,
    // since the route was found with them in mind and the route screen already told the walker.
    val eventRepository = remember { EventRepository.create() }
    val knownEvents = remember { mutableSetOf<Long>() }
    var eventAhead by remember { mutableStateOf<EventAhead?>(null) }
    LaunchedEffect(eventAhead) { eventAhead?.let { onLive(false, eventAheadText(it)) } }
    var firstEventCheck by remember { mutableStateOf(true) }
    val progressNow by rememberUpdatedState(state.progressM)
    LaunchedEffect(guidance) {
        while (true) {
            val events = try {
                eventRepository.within(bboxAround(route.path, marginDeg = 0.004))
            } catch (e: ApiException) {
                null
            }
            if (events != null) {
                if (firstEventCheck) {
                    knownEvents += events.map { it.id }
                    firstEventCheck = false
                } else {
                    eventsAhead(guidance, progressNow, events, knownEvents).firstOrNull()?.let { ahead ->
                        knownEvents += ahead.event.id
                        eventAhead = ahead
                        if (voiceOn) tts.speak(eventAheadText(ahead), TextToSpeech.QUEUE_FLUSH, null, "event")
                    }
                }
            }
            delay(EVENT_CHECK_MS)
        }
    }

    // Keep the walker low in the part of the map not covered by the instruction card, so more of the way
    // ahead shows above it (11/10: 가운데는 너무 위쪽). The map's center sits halfway down what the top padding
    // leaves, so this padding puts it [WALKER_DOWN] of the way down the free part.
    var overlayBottomPx by remember { mutableIntStateOf(0) }
    var mapHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(map, overlayBottomPx, mapHeightPx) {
        val m = map ?: return@LaunchedEffect
        m.setPadding(0, walkerTopPaddingPx(overlayBottomPx, mapHeightPx), 0, 0)
        m.moveCamera(CameraUpdateFactory.newCenterPosition(state.snapped.toLatLng(), GUIDE_ZOOM))
    }

    // Closes by itself after a while, so it does not stay over the map while walking.
    LaunchedEffect(tapped) { if (tapped != null) { delay(TAPPED_CARD_MS); tapped = null } }

    // Walking much slower than usual for a while (seniors and wheelchair users): offer a place to rest.
    val paceWatch = remember(speed) { PaceWatch(speed / 60.0) }
    val demoPaceWatch = remember { PaceWatch(DEMO_STEP_M, DEMO_SLOW_WINDOW_MS) }
    var restOffer by remember { mutableStateOf<RestPlace?>(null) }
    var lastRestAsk by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.progressM) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (inVehicle) { paceWatch.reset(); demoPaceWatch.reset(); return@LaunchedEffect }
        val watch = if (demo) demoPaceWatch.also { paceWatch.reset() } else paceWatch.also { demoPaceWatch.reset() }
        watch.add(state.progressM, now)
        val due = lastRestAsk?.let { now - it >= REST_ASK_AGAIN_MS } ?: true
        if (!asksToRest(mode) || resting != null || restOffer != null || !due || state.remainingM <= REST_NOT_NEAR_END_M) return@LaunchedEffect
        if (!watch.slow) return@LaunchedEffect
        // Where the walker is drawn: in the demo walk that is on the route, not the phone's far-away position.
        val night = active.conditions.any { it.key == "night" && it.on }
        val place = restPlaceOnTheWay(active.facilities, if (demo) state.snapped else fix ?: state.snapped, state.progressM, night) {
            guidance.alongAndOff(LatLon(it.lat, it.lon))
        } ?: return@LaunchedEffect
        lastRestAsk = now
        restOffer = place
        val said = restOfferText(place)
        onLive(false, said)
        if (voiceOn) tts.speak(said, TextToSpeech.QUEUE_FLUSH, null, "rest")
    }
    fun goRest(place: RestPlace) {
        onLive(true, "쉬었다 갈게요")
        restOffer = null
        resting = place
        restReached = false
        aiNotice = "가는 길 ${place.meters}m 앞 ${restName(place)}에서 쉬어 가요. 쉬고 나서 같은 길로 이어서 안내해요."
    }
    fun goOnAfterRest() {
        onLive(true, "다시 출발")
        restReached = true
        resting = null
        paceWatch.reset()
        demoPaceWatch.reset()
        aiNotice = "다시 출발할게요. ${destinationName}까지 같은 길로 안내해요."
    }
    LaunchedEffect(atRest) {
        val place = resting ?: return@LaunchedEffect
        if (!atRest) return@LaunchedEffect
        val said = restArrivedText(place)
        onLive(false, said)
        if (voiceOn) tts.speak(said, TextToSpeech.QUEUE_FLUSH, null, "rested")
    }

    // Reaching the next stop: say where, and what comes next, until [다 봤어요].
    val stopNames = plannedStops.map { it.name }
    val reached = if (atStop == null) stopReached(leftAlong, state.progressM, emptySet())?.let { leftStops[it] } else null
    LaunchedEffect(reached) {
        val i = reached ?: return@LaunchedEffect
        atStop = i
        val text = stopArrivedText(stopNames, i, stopsDone, destinationName)
        onLive(false, text)
        if (voiceOn) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "stop")
    }

    // The arrival time moves only when it changes by 2 minutes or more, so it does not flick 10:01 ↔ 10:02 as
    // the minutes round (11/9 video).
    val arrivalNow = java.time.LocalTime.now().plusMinutes(walkMinutes(state.remainingM.toDouble(), speed).toLong())
    var arrivalShown by remember { mutableStateOf<java.time.LocalTime?>(null) }
    val keepArrival = arrivalShown?.let { steadyArrival(it, arrivalNow) }
    if (keepArrival != arrivalShown) SideEffect { arrivalShown = keepArrival }
    val shownArrival = (keepArrival ?: arrivalNow).format(java.time.format.DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))

    Box(modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            KakaoMapView(modifier = Modifier.fillMaxSize().onSizeChanged { mapHeightPx = it.height }, onMapReady = { map = it })
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(12.dp)
                    .onGloballyPositioned { overlayBottomPx = it.boundsInParent().bottom.toInt() },
            ) {
                InstructionCard(state, instruction)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(if (type == RouteType.SAFE) "안전한 길로 가는 중" else "빠른 길로 가는 중", Green)
                    if (demo) StatusPill("시연: 가상 이동", Color(0xFF6D28D9))
                }
                if (!demo && !atRest && state.offRouteM > offLimit) {
                    StatusPill("경로에서 ${state.offRouteM}m 벗어났어요", Color(0xFFB42318))
                }
                if (rerouting) StatusPill("길을 다시 찾는 중…", Color(0xFF2F6FDB))
                resting?.let { if (!atRest) StatusPill("${restName(it)}에서 쉬었다 가는 중" + (restAlongM?.let { a -> " · ${(a - state.progressM).coerceAtLeast(0.0).toInt()}m 앞" } ?: ""), Color(0xFFB45309)) }
                restOffer?.let { offer ->
                    RestCard(restOfferText(offer), yes = "쉬었다 갈게요", no = "괜찮아요", onYes = { goRest(offer) },
                        onNo = { onLive(true, "괜찮아요"); restOffer = null })
                }
                resting?.takeIf { atRest }?.let { place ->
                    RestCard(restArrivedText(place), yes = "다시 출발", no = null, onYes = { goOnAfterRest() }, onNo = {})
                }
                if (inVehicle) StatusPill("차로 이동 중 · 걸으면 다시 안내해요", Color(0xFF6D28D9))
                runShown?.let { RunWarningBar(it) }
                // Which side to walk on beside a road, when the map knows it.
                sidewalkHint(route.sidewalks, guidance.pointIndexAt(state.progressM))?.let { hint ->
                    StatusPill(hint, if (hint.startsWith("인도가 없어요")) Color(0xFFB45309) else Color(0xFF2F6FDB))
                }
                if ((offRouteLong || rerouted) && guardians.isNotEmpty() && !offRouteTold) {
                    OffRouteCard(
                        rerouted = rerouted,
                        onTell = {
                            sendGuardianText(context, guardians, guardianMessage(GuardianAlert.OFF_ROUTE, fix ?: state.snapped))
                            offRouteTold = true
                            rerouted = false
                        },
                        onDismiss = { offRouteTold = true; rerouted = false },
                    )
                }
                atStop?.let { i ->
                    StopArrivedCard(
                        stopArrivedText(stopNames, i, stopsDone, destinationName),
                        onDone = {
                            onLive(true, "다 봤어요")
                            stopsDone = stopsDone + i
                            atStop = null
                        },
                    )
                } ?: stopsLeftText(stopNames, stopsDone)?.let { left ->
                    StopsLeftRow(
                        left,
                        finding = rerouting,
                        onSkip = {
                            val i = leftStops.first()
                            onLive(true, "이곳은 안 갈게요")
                            stopsDone = stopsDone + i
                            scope.launch {
                                if (findNewRoute(fix ?: state.snapped, "${stopNames[i]}에는 안 들르는 길을 찾았어요")) {
                                    aiNotice = "${stopNames[i]}에는 안 들르고 지금 자리에서 새 길을 찾았어요."
                                }
                            }
                        },
                    )
                }
                if (askRefind) {
                    RefindCard(
                        finding = rerouting,
                        onRefind = {
                            scope.launch {
                                onLive(true, REFIND_YES)
                                if (findNewRoute(fix ?: state.snapped, REFIND_DONE)) {
                                    askRefind = false
                                    aiNotice = REFIND_DONE
                                } else {
                                    askRefind = false
                                    aiNotice = "새 길을 찾지 못해서 지금 길로 안내할게요."
                                }
                            }
                        },
                        onDismiss = { onLive(true, REFIND_NO); askRefind = false },
                    )
                } else
                // Something new on the way, in AI 비서's words, here under the status line (team, 11/6).
                eventAhead?.let { ahead ->
                    EventAheadCard(
                        ahead,
                        finding = rerouting,
                        onReroute = {
                            scope.launch {
                                onLive(true, "다른 길 찾기")
                                if (findNewRoute(fix ?: state.snapped, "${ahead.event.kind} 구간을 피해 새 길을 찾았어요")) {
                                    eventAhead = null
                                    aiNotice = "${ahead.event.kind} 구간을 피해 지금 자리에서 새 길을 찾았어요."
                                }
                            }
                        },
                        onDismiss = { onLive(true, "그냥 갈게요"); eventAhead = null },
                    )
                } ?: aiNotice?.let { AiNotice(it, onDismiss = { aiNotice = null }) }
            }
            if (showSos) SosButton(onClick = { sos = true }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
            tapped?.let { place ->
                PlaceDetailCard(
                    place, exits = emptyList(), onClose = { tapped = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                )
            }
            // The mode on the instruction card's top left corner.
            ModeBadge(mode, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 4.dp))
            onOpenAssistant?.let { open ->
                Surface(
                    onClick = open,
                    shape = RoundedCornerShape(14.dp),
                    color = Green,
                    contentColor = Color.White,
                    shadowElevation = 6.dp,
                    // Right above the location button; the same green 'AI 비서' button as on the map, made small.
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 80.dp).size(52.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("AI 비서", fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
            }
            // 불편 신고 while walking: above AI 비서, in the report orange.
            onOpenReport?.let { open ->
                Surface(
                    onClick = open,
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFB45309),
                    contentColor = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (onOpenAssistant != null) 144.dp else 80.dp).size(52.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("신고", fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
            }
            // The camera follows only when the position moves, so after dragging the map this brings it back.
            Surface(
                onClick = { map?.moveCamera(CameraUpdateFactory.newCenterPosition(state.snapped.toLatLng(), GUIDE_ZOOM)) },
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "내 위치로 이동", tint = Color(0xFF2F6FDB))
                }
            }
        }

        // Kept short so most of the screen is map: one line for help, one for the numbers, one row of buttons.
        Surface(shadowElevation = 8.dp, color = Color.White) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                // Nearest help place, or how many lie ahead; tapping opens the whole list (도움 지점 목록).
                val ahead = guidance.helpAhead(state.progressM)
                if (state.nearestHelp != null || ahead.isNotEmpty()) {
                    val help = state.nearestHelp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF4E5), RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "도움 장소 목록 보기") { showHelpList = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(26.dp).background(Color(help?.kind?.color ?: 0xFF7C2D12), CircleShape),
                        ) { Text("!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
                        Text(
                            if (help != null) "가까운 도움 ${help.kind.label} · ${sideText(help.side)} ${help.meters}m"
                            else "가는 길에 도움 장소 ${ahead.size}곳",
                            // Two lines rather than cutting off the side and distance ("오른쪽 …", 11/9 video).
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 21.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text("목록", fontSize = 14.sp, color = Color(0xFF7C2D12), fontWeight = FontWeight.Bold)
                    }
                }

                Row(Modifier.fillMaxWidth()) {
                    val minutes = walkMinutes(state.remainingM.toDouble(), speed)
                    Stat("남은 거리", formatDistance(state.remainingM), Modifier.weight(1f))
                    Stat("남은 시간", "${minutes}분", Modifier.weight(1f))
                    Stat("도착", shownArrival, Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { voiceOn = !voiceOn },
                        border = BorderStroke(2.dp, if (voiceOn) Green else Color(0xFFCBD3CC)),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(if (voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (voiceOn) "음성 켬" else "음성 끔", fontSize = 15.sp, color = Ink, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                tts.speak(instruction.spoken, TextToSpeech.QUEUE_FLUSH, null, "again")
                                map?.moveCamera(CameraUpdateFactory.newCenterPosition(state.snapped.toLatLng(), GUIDE_ZOOM))
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("다시 안내", fontSize = 15.sp, color = Ink, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onFinish,
                        border = BorderStroke(2.dp, if (state.arrived) Ink else Color(0xFFCBD3CC)),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(finishLabel ?: if (state.arrived && resting == null) "도착! 끝내기" else "안내 끝내기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                    }
                }
            }
        }
    }
    if (showHelpList) HelpListDialog(guidance.helpAhead(state.progressM), onDismiss = { showHelpList = false })

    // SOS covers the whole screen, guidance included.
    if (sos) SosOverlay(guardians, here = { fix ?: state.snapped }, onClose = { sos = false }, onAddGuardian = null)
    }
}

private data class Instruction(val big: String, val action: String, val spoken: String)

/** Closer than this to the turn, say "지금" instead of "0m 앞". */
private const val TURN_NOW_M = 5

private fun instructionText(state: GuidanceState): Instruction {
    val m = state.metersToNextTurn
    return when {
        state.arrived -> Instruction("도착", "목적지에 도착했어요", "목적지에 도착했어요. 안내를 마칩니다.")
        m < TURN_NOW_M && state.nextTurn == Turn.LEFT -> Instruction("지금", "왼쪽으로 가세요", "지금 왼쪽으로 가세요")
        m < TURN_NOW_M && state.nextTurn == Turn.RIGHT -> Instruction("지금", "오른쪽으로 가세요", "지금 오른쪽으로 가세요")
        state.nextTurn == Turn.LEFT -> Instruction("${m}m 앞", "왼쪽으로 가세요", "${m}미터 앞에서 왼쪽으로 가세요")
        state.nextTurn == Turn.RIGHT -> Instruction("${m}m 앞", "오른쪽으로 가세요", "${m}미터 앞에서 오른쪽으로 가세요")
        state.nextTurn == Turn.STRAIGHT -> Instruction("${m}m", "쭉 가세요", "${m}미터 쭉 가세요")
        else -> Instruction("${m}m 앞", "목적지예요", "${m}미터 앞에 목적지가 있어요")
    }
}

/** Speak again only when the turn changes or the walker gets noticeably closer. */
private fun distanceBucket(meters: Int) = when {
    meters > 100 -> 2
    meters > 30 -> 1
    else -> 0
}

private fun sideText(side: Turn) = when (side) {
    Turn.LEFT -> "왼쪽"
    Turn.RIGHT -> "오른쪽"
    else -> "앞"
}

@Composable
private fun InstructionCard(state: GuidanceState, instruction: Instruction) {
    // Slightly see-through so the map under it still shows.
    // Thin bar: distance and action side by side so the map keeps most of the screen.
    // Solid, not see-through: the map behind made it paler in sunlight (사용성 16번, 11/9).
    Surface(shape = RoundedCornerShape(16.dp), color = Ink, contentColor = Color.White) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).background(Color(0xFF2E8B57), RoundedCornerShape(12.dp)),
            ) {
                val icon = when {
                    state.arrived || state.nextTurn == Turn.ARRIVE -> Icons.Filled.Flag
                    state.nextTurn == Turn.LEFT -> Icons.Filled.TurnLeft
                    state.nextTurn == Turn.RIGHT -> Icons.Filled.TurnRight
                    else -> Icons.Filled.Straight
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(instruction.big, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(instruction.action, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(label, fontSize = 13.sp, color = Muted)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink)
    }
}

@Composable
internal fun rememberTts(context: Context): TextToSpeech {
    val tts = remember {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) engine.setLanguage(Locale.KOREAN)
        }
        engine
    }
    DisposableEffect(tts) { onDispose { tts.stop(); tts.shutdown() } }
    return tts
}

private fun formatDistance(meters: Int): String =
    if (meters < 1000) "${meters}m" else "%.1fkm".format(meters / 1000.0)

/** How long the running warning stays on the screen. */
private const val RUN_SHOWN_MS = 6_000L

/** The running warning, big and red under the instruction card. */
@Composable
private fun RunWarningBar(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFB42318), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Text(text, fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 25.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** Two strong buzzes and a beep, for a running child (뛰기 경고). */
private suspend fun warnRunning(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    }
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        delay(600)
        tone.release()
    }
}

/** The arrival time to show: [shown] until [now] differs from it by 2 minutes or more, then [now]. */
internal fun steadyArrival(shown: java.time.LocalTime, now: java.time.LocalTime): java.time.LocalTime =
    if (kotlin.math.abs(java.time.Duration.between(shown, now).toMinutes()) >= 2) now else shown

/** AI 비서's rest card: the offer with [yes]/[no], or at the rest place [다시 출발] alone ([no] null). */
@Composable
private fun RestCard(text: String, yes: String, no: String?, onYes: () -> Unit, onNo: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF4E5).copy(alpha = AI_BUBBLE_ALPHA),
        border = BorderStroke(1.5.dp, Color(0xFFB45309)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "AI 비서", tint = Color(0xFFB45309), modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17211B), lineHeight = 24.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onYes,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text(yes, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                no?.let {
                    OutlinedButton(onClick = onNo, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(it, fontSize = 16.sp, color = Color(0xFF17211B)) }
                }
            }
        }
    }
}

/** Shown after a while off the route: tell the guardians where the walker is, or dismiss. */
/** AI 비서's short word under the status line, e.g. that a new route was found. */
@Composable
private fun AiNotice(text: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = AI_BUBBLE_ALPHA),
        border = BorderStroke(1.5.dp, Green),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "AI 비서", tint = Green, modifier = Modifier.size(18.dp))
            Text(text, fontSize = 16.sp, color = Color(0xFF17211B), lineHeight = 22.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Color(0xFF5E6A62)) }
        }
    }
}

/** 들를 곳 left: "들를 곳 2/3 다이소" with [이곳은 안 갈게요] to skip it. */
@Composable
private fun StopsLeftRow(left: String, finding: Boolean, onSkip: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = AI_BUBBLE_ALPHA),
        border = BorderStroke(1.5.dp, Color(0xFFB45309)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)) {
            Text("들를 곳 $left", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A3E06), modifier = Modifier.weight(1f))
            TextButton(onClick = onSkip, enabled = !finding, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("이곳은 안 갈게요", fontSize = 15.sp, color = Color(0xFFB45309))
            }
        }
    }
}

/** At a stop: "다이소에 도착했어요. 다음은 CU예요" and [다 봤어요]. */
@Composable
private fun StopArrivedCard(text: String, onDone: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF4E5).copy(alpha = AI_BUBBLE_ALPHA),
        border = BorderStroke(1.5.dp, Color(0xFFB45309)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17211B), lineHeight = 25.sp)
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("다 봤어요", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Asked after a report during the guidance, and its answers. */
const val REFIND_QUESTION = "신고했어요. 가시던 곳으로 다시 찾을까요?"
const val REFIND_YES = "다시 찾기"
const val REFIND_NO = "이대로 갈게요"
const val REFIND_DONE = "지금 자리에서 가시던 곳까지 길을 다시 찾았어요."

/** After a report: 다시 찾기 (a new route from here to the same place) or carrying on. */
@Composable
private fun RefindCard(finding: Boolean, onRefind: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF4E5).copy(alpha = AI_BUBBLE_ALPHA),
        border = BorderStroke(1.5.dp, Color(0xFFB45309)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
                Text("AI 비서", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Green)
            }
            Text(REFIND_QUESTION, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17211B), lineHeight = 23.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRefind,
                    enabled = !finding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6B42)),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(if (finding) "찾는 중…" else REFIND_YES, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(REFIND_NO, fontSize = 16.sp)
                }
            }
        }
    }
}

/** A new road event ahead: what and where, with 다른 길 찾기 or carrying on. */
@Composable
private fun EventAheadCard(
    ahead: EventAhead,
    finding: Boolean,
    onReroute: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF4E5).copy(alpha = AI_BUBBLE_ALPHA),
        border = BorderStroke(1.5.dp, Color(0xFFB45309)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
                Text("AI 비서", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Green)
                Text("· 새로 생긴 ${ahead.event.kind}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A5310))
            }
            Text(eventAheadText(ahead), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17211B), lineHeight = 23.sp)
            listOfNotNull(ahead.event.place, ahead.event.message).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 14.sp, color = Color(0xFF5E6A62), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReroute,
                    enabled = !finding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6B42)),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(if (finding) "찾는 중…" else "다른 길 찾기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("그냥 갈게요", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun OffRouteCard(rerouted: Boolean, onTell: () -> Unit, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 4.dp) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp)) {
            Text(
                if (rerouted) "정한 길에서 벗어나 새 길로 안내해요" else "정한 길에서 벗어났어요",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB42318),
            )
            Row {
                TextButton(onClick = onTell) { Text("보호자에게 알리기", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDismiss) { Text("괜찮아요", fontSize = 15.sp) }
            }
        }
    }
}

/** 도움 지점 목록: every help place along the rest of the route, nearest first. */
@Composable
private fun HelpListDialog(places: List<HelpAhead>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("가는 길의 도움 장소") },
        text = {
            if (places.isEmpty()) {
                Text("남은 길 가까이에 도움을 청할 곳이 없어요.", fontSize = 16.sp)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    places.forEach { place ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(12.dp).background(Color(place.kind.color), CircleShape))
                            Text(place.kind.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                            Text(
                                (if (place.aheadM < 10) "바로 앞" else "${formatDistance(place.aheadM)} 앞") + " · " +
                                    if (place.side == Turn.STRAIGHT) "길 위" else sideText(place.side),
                                fontSize = 15.sp, color = Muted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
