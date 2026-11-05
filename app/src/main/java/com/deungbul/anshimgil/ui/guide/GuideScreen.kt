package com.deungbul.anshimgil.ui.guide

import androidx.compose.material3.TextButton
import com.deungbul.anshimgil.ui.safety.openGuardianText
import com.deungbul.anshimgil.ui.safety.SosOverlay
import com.deungbul.anshimgil.ui.safety.SosButton
import com.deungbul.anshimgil.data.guardianMessage
import com.deungbul.anshimgil.data.GuardianAlert
import com.deungbul.anshimgil.data.Guardian
import com.kakao.vectormap.camera.CameraAnimation
import com.deungbul.anshimgil.data.FacilityRepository
import com.deungbul.anshimgil.data.ApiException
import com.deungbul.anshimgil.data.bboxAround
import com.deungbul.anshimgil.map.addTurnArrows
import com.deungbul.anshimgil.map.addCrosswalks
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.deungbul.anshimgil.data.Guidance
import com.deungbul.anshimgil.data.GuidanceState
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.RouteResult
import com.deungbul.anshimgil.data.RouteType
import com.deungbul.anshimgil.data.Turn
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.data.walkingSpeedMPerMin
import com.deungbul.anshimgil.map.KakaoMapView
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
private const val OFF_ROUTE_WARN_M = 40
/** Off the route this long before offering to tell the guardians. */
private const val OFF_ROUTE_ALERT_MS = 20_000L
private const val DEMO_STEP_M = 8.0
/** Close-up for walking: the next few buildings, with the map tilted so the street ahead shows. */
private const val GUIDE_ZOOM = 19
/** A crosswalk this close to the route line is one the route crosses. */
private const val CROSSWALK_ON_ROUTE_M = 8.0
/** Position updates come about once a second; moving over slightly less keeps the marker gliding. */
private const val FOLLOW_MS = 900
private val FOLLOW = CameraAnimation.from(FOLLOW_MS, false, true)
/** How long the map and the arrow take to turn. */
private const val TURN_MS = 300

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
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sos by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val route = result.route(type) ?: result.routes.first()
    val guidance = remember(route) { Guidance(route.path, result.facilitiesOn(route.type)) }
    var fix by remember { mutableStateOf<LatLon?>(null) }
    var demoProgress by remember { mutableDoubleStateOf(0.0) }
    var voiceOn by rememberSaveable { mutableStateOf(voiceByDefault) }
    var map by remember { mutableStateOf<KakaoMap?>(null) }
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
    if (hasPermission) LocationUpdates(context) { fix = it }

    val realState = fix?.let { guidance.update(it) }
    val demo = realState == null || realState.offRouteM > FAR_FROM_ROUTE_M
    val state: GuidanceState = if (demo) guidance.atProgress(demoProgress) else realState!!

    // Demo walk: move along the route a few meters per second.
    LaunchedEffect(demo) {
        while (demo && demoProgress < guidance.totalM) {
            delay(1000)
            demoProgress += DEMO_STEP_M
        }
    }

    val tts = rememberTts(context)
    val instruction = instructionText(state)
    val spokenKey = "${state.nextTurn}-${distanceBucket(state.metersToNextTurn)}-${state.arrived}"
    LaunchedEffect(spokenKey, voiceOn) {
        if (voiceOn) tts.speak(instruction.spoken, TextToSpeech.QUEUE_FLUSH, null, "guide")
    }

    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        m.showRoutes(context, result.copy(routes = listOf(route)), route.type, destinationName)
        m.addTurnArrows(context, guidance.turnMarks)
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
    LaunchedEffect(map, state.snapped) {
        // Glide to each new position over about the time until the next fix, instead of jumping.
        me?.moveTo(state.snapped.toLatLng(), FOLLOW_MS)
        map?.moveCamera(CameraUpdateFactory.newCenterPosition(state.snapped.toLatLng(), GUIDE_ZOOM), FOLLOW)
    }

    // The way the walker faces: the phone's compass. In the demo walk, or without a compass,
    // the way ahead on the route instead.
    val compass = rememberCompassHeading()
    val travel = guidance.travelBearing(state.progressM)
    val heading = if (demo || compass == null) travel else compass

    // The map stays north-up so the turn signs and streets keep still on screen while walking;
    // only the walker's arrow turns to show which way they face.
    LaunchedEffect(me, heading) { me?.pointTo(heading, TURN_MS) }

    // 귀가 지킴이: off the planned route for a while, offer to tell the guardians (once per stretch off route).
    val off = !demo && state.offRouteM > OFF_ROUTE_WARN_M
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

    // Keep the walker in the middle of the part of the map not covered by the instruction card:
    // the map's top padding moves its center down by the card's height.
    var overlayBottomPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(map, overlayBottomPx) {
        val m = map ?: return@LaunchedEffect
        m.setPadding(0, overlayBottomPx, 0, 0)
        m.moveCamera(CameraUpdateFactory.newCenterPosition(state.snapped.toLatLng(), GUIDE_ZOOM))
    }

    Box(modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            KakaoMapView(modifier = Modifier.fillMaxSize(), onMapReady = { map = it })
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
                if (!demo && state.offRouteM > OFF_ROUTE_WARN_M) {
                    StatusPill("경로에서 ${state.offRouteM}m 벗어났어요", Color(0xFFB42318))
                }
                if (offRouteLong && guardians.isNotEmpty() && !offRouteTold) {
                    OffRouteCard(
                        onTell = {
                            openGuardianText(context, guardians, guardianMessage(GuardianAlert.OFF_ROUTE, fix ?: state.snapped))
                            offRouteTold = true
                        },
                        onDismiss = { offRouteTold = true },
                    )
                }
            }
            SosButton(onClick = { sos = true }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
            // The camera follows only when the position moves, so after dragging the map this brings it back.
            Surface(
                onClick = { map?.moveCamera(CameraUpdateFactory.newCenterPosition(state.snapped.toLatLng(), GUIDE_ZOOM)) },
                shape = CircleShape,
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
                state.nearestHelp?.let { help ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF4E5), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(26.dp).background(Color(help.kind.color), CircleShape),
                        ) { Text("!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
                        Text(
                            "가까운 도움  ${help.kind.label} · ${sideText(help.side)} ${help.meters}m",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(Modifier.fillMaxWidth()) {
                    val minutes = (state.remainingM.toDouble() / mode.walkingSpeedMPerMin).let { kotlin.math.ceil(it).toInt() }
                    Stat("남은 거리", formatDistance(state.remainingM), Modifier.weight(1f))
                    Stat("남은 시간", "${minutes}분", Modifier.weight(1f))
                    Stat("도착", arrivalTime(minutes), Modifier.weight(1f))
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
                        Text(if (state.arrived) "도착! 끝내기" else "안내 끝내기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                    }
                }
            }
        }
    }
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
    Surface(shape = RoundedCornerShape(16.dp), color = Ink.copy(alpha = 0.82f), contentColor = Color.White) {
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
private fun rememberTts(context: Context): TextToSpeech {
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

/** Shown after a while off the route: tell the guardians where the walker is, or dismiss. */
@Composable
private fun OffRouteCard(onTell: () -> Unit, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 4.dp) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp)) {
            Text("정한 길에서 벗어났어요", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB42318))
            Row {
                TextButton(onClick = onTell) { Text("보호자에게 알리기", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDismiss) { Text("괜찮아요", fontSize = 15.sp) }
            }
        }
    }
}
