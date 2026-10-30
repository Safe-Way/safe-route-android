package com.deungbul.anshimgil.ui.guide

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import androidx.core.content.ContextCompat
import com.deungbul.anshimgil.data.Guidance
import com.deungbul.anshimgil.data.GuidanceState
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.RouteResult
import com.deungbul.anshimgil.data.RouteType
import com.deungbul.anshimgil.data.Turn
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.data.walkingSpeedMPerMin
import com.deungbul.anshimgil.map.KakaoMapView
import com.deungbul.anshimgil.map.dotBitmap
import com.deungbul.anshimgil.map.showRoutes
import com.deungbul.anshimgil.map.toLatLng
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF4A564E)
private val Green = Color(0xFF1F6B42)

/** A real fix farther than this from the route switches to the demo walk (e.g. testing away from the route). */
private const val FAR_FROM_ROUTE_M = 300
private const val OFF_ROUTE_WARN_M = 40
private const val DEMO_STEP_M = 8.0

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
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val route = result.route(type) ?: result.routes.first()
    val guidance = remember(route) { Guidance(route.path, result.facilities) }
    var fix by remember { mutableStateOf<LatLon?>(null) }
    var demoProgress by remember { mutableDoubleStateOf(0.0) }
    var voiceOn by rememberSaveable { mutableStateOf(true) }
    var map by remember { mutableStateOf<KakaoMap?>(null) }
    var me by remember { mutableStateOf<Label?>(null) }
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = hasLocationPermission(context)
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
        val density = context.resources.displayMetrics.density
        me = m.labelManager?.layer?.addLabel(
            LabelOptions.from(state.snapped.toLatLng())
                .setStyles(LabelStyles.from(LabelStyle.from(dotBitmap(0xFF2F6FDB.toInt(), density, 24f)).setApplyDpScale(false)))
                .setRank(10),
        )
    }
    LaunchedEffect(map, state.snapped) {
        me?.moveTo(state.snapped.toLatLng())
        map?.moveCamera(CameraUpdateFactory.newCenterPosition(state.snapped.toLatLng(), 17))
    }

    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            KakaoMapView(modifier = Modifier.fillMaxSize(), onMapReady = { map = it })
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
                InstructionCard(state, instruction)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(if (type == RouteType.SAFE) "안전한 길로 가는 중" else "빠른 길로 가는 중", Green)
                    if (demo) StatusPill("시연: 가상 이동", Color(0xFF6D28D9))
                }
                if (!demo && state.offRouteM > OFF_ROUTE_WARN_M) {
                    StatusPill("경로에서 ${state.offRouteM}m 벗어났어요", Color(0xFFB42318))
                }
            }
        }

        Surface(shadowElevation = 8.dp, color = Color.White) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(16.dp)) {
                state.nearestHelp?.let { help ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF4E5), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(40.dp).background(Color(help.kind.color), CircleShape),
                        ) { Text("!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
                        Column {
                            Text("가까운 도움 장소", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C2D12))
                            Text(
                                "${help.kind.label} · ${sideText(help.side)} ${help.meters}m",
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink,
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth()) {
                    val minutes = (state.remainingM.toDouble() / mode.walkingSpeedMPerMin).let { kotlin.math.ceil(it).toInt() }
                    Stat("남은 거리", formatDistance(state.remainingM), Modifier.weight(1f))
                    Stat("남은 시간", "${minutes}분", Modifier.weight(1f))
                    Stat("도착", LocalTime.now().plusMinutes(minutes.toLong()).format(ARRIVAL_FORMAT), Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { voiceOn = !voiceOn },
                        border = BorderStroke(2.dp, if (voiceOn) Green else Color(0xFFCBD3CC)),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Icon(if (voiceOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null, tint = Ink)
                        Spacer(Modifier.width(6.dp))
                        Text(if (voiceOn) "음성 켜짐" else "음성 꺼짐", fontSize = 15.sp, color = Ink)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                tts.speak(instruction.spoken, TextToSpeech.QUEUE_FLUSH, null, "again")
                                map?.moveCamera(CameraUpdateFactory.newCenterPosition(state.snapped.toLatLng(), 17))
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = null, tint = Ink)
                        Spacer(Modifier.width(6.dp))
                        Text("다시 안내", fontSize = 15.sp, color = Ink)
                    }
                }
                OutlinedButton(
                    onClick = onFinish,
                    border = BorderStroke(2.dp, if (state.arrived) Ink else Color(0xFFCBD3CC)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(if (state.arrived) "도착! 안내 끝내기" else "안내 끝내기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }
    }
}

private data class Instruction(val big: String, val action: String, val spoken: String)

private fun instructionText(state: GuidanceState): Instruction {
    val m = state.metersToNextTurn
    return when {
        state.arrived -> Instruction("도착", "목적지에 도착했어요", "목적지에 도착했어요. 안내를 마칩니다.")
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
    Surface(shape = RoundedCornerShape(20.dp), color = Ink, contentColor = Color.White, shadowElevation = 6.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).background(Color(0xFF2E8B57), RoundedCornerShape(16.dp)),
            ) {
                val icon = when {
                    state.arrived || state.nextTurn == Turn.ARRIVE -> Icons.Filled.Flag
                    state.nextTurn == Turn.LEFT -> Icons.Filled.TurnLeft
                    state.nextTurn == Turn.RIGHT -> Icons.Filled.TurnRight
                    else -> Icons.Filled.Straight
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            Column {
                Text(instruction.big, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text(instruction.action, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
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

/** GPS fixes while this screen is shown; stops when it leaves. */
@SuppressLint("MissingPermission")
@Composable
private fun LocationUpdates(context: Context, onFix: (LatLon) -> Unit) {
    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { location: Location -> onFix(LatLon(location.latitude, location.longitude)) }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        providers.forEach { manager.requestLocationUpdates(it, 1000L, 0f, listener) }
        onDispose { manager.removeUpdates(listener) }
    }
}

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private val ARRIVAL_FORMAT = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

private fun formatDistance(meters: Int): String =
    if (meters < 1000) "${meters}m" else "%.1fkm".format(meters / 1000.0)
