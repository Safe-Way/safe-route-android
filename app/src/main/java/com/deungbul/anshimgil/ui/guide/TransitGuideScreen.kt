package com.deungbul.anshimgil.ui.guide

import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deungbul.anshimgil.data.ALIGHT_NEAR_M
import com.deungbul.anshimgil.data.ApiException
import com.deungbul.anshimgil.data.FacilityRepository
import com.deungbul.anshimgil.data.Guardian
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.LegType
import com.deungbul.anshimgil.data.RouteRepository
import com.deungbul.anshimgil.data.RouteRequest
import com.deungbul.anshimgil.data.RouteResult
import com.deungbul.anshimgil.data.RouteType
import com.deungbul.anshimgil.data.TransitLeg
import com.deungbul.anshimgil.data.TransitRoute
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.data.WalkSpeedRepository
import com.deungbul.anshimgil.data.distanceMeters
import com.deungbul.anshimgil.data.guideLegs
import com.deungbul.anshimgil.data.legLabel
import com.deungbul.anshimgil.data.nextRideText
import com.deungbul.anshimgil.data.rideDetail
import com.deungbul.anshimgil.data.rideSpoken
import com.deungbul.anshimgil.data.rideTitle
import com.deungbul.anshimgil.data.walkResult
import com.deungbul.anshimgil.data.walkTitle
import com.deungbul.anshimgil.location.LocationUpdates
import com.deungbul.anshimgil.location.hasLocationPermission

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF5E6A62)
private val Green = Color(0xFF1F6B42)
private val Blue = Color(0xFF2F5BB7)

/**
 * 대중교통 길 안내: [route]'s parts in order. A walk is the usual walking guidance (its finish button goes
 * on to the next part); a ride is a card with what to take and where to get off, and 내렸어요 goes on.
 */
@Composable
fun TransitGuideScreen(
    mode: UserMode,
    route: TransitRoute,
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
) {
    val legs = remember(route) { guideLegs(route) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val leg = legs.getOrNull(step) ?: run { LaunchedEffect(Unit) { onFinish() }; return }
    val last = step == legs.lastIndex
    fun next() = if (last) onFinish() else step++
    val nextLabel = legs.getOrNull(step + 1)?.let { n -> if (n.type == LegType.WALK) "다음: 걷기" else "다음: ${legLabel(n)} 타기" }

    Column(modifier.fillMaxSize().background(Color.White)) {
        StepStrip(legs, step)
        Box(Modifier.weight(1f)) {
            key(step) {
                if (leg.type == LegType.WALK) {
                    WalkPart(
                        leg, last, destinationName, mode, voiceByDefault, facilityRepository, guardians, showTurnSigns,
                        showSos, routeRepository, walkSpeedRepository,
                        finishLabel = nextLabel ?: "도착! 끝내기",
                        onDone = ::next,
                    )
                } else {
                    RidePart(leg, voiceByDefault, nextLabel = nextLabel, onDone = ::next, onStop = onFinish)
                }
            }
        }
    }
}

/** 걷기 → 21번 → 걷기, with the part now in bold. */
@Composable
private fun StepStrip(legs: List<TransitLeg>, step: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(Color(0xFFEFF4FB)).horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        legs.forEachIndexed { i, l ->
            if (i > 0) Text("  →  ", fontSize = 14.sp, color = Muted)
            Text(
                legLabel(l),
                fontSize = 15.sp,
                fontWeight = if (i == step) FontWeight.Black else FontWeight.Normal,
                color = when {
                    i == step -> if (l.type == LegType.WALK) Green else Blue
                    i < step -> Color(0xFF9AA49D)
                    else -> Ink
                },
            )
        }
    }
}

@Composable
private fun WalkPart(
    leg: TransitLeg,
    last: Boolean,
    destinationName: String,
    mode: UserMode,
    voiceByDefault: Boolean,
    facilityRepository: FacilityRepository,
    guardians: List<Guardian>,
    showTurnSigns: Boolean,
    showSos: Boolean,
    routeRepository: RouteRepository,
    walkSpeedRepository: WalkSpeedRepository,
    finishLabel: String,
    onDone: () -> Unit,
) {
    // The server's walking line when it gave one; otherwise our own walking route to the stop.
    var result by remember { mutableStateOf(walkResult(leg)) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (result != null) return@LaunchedEffect
        result = try {
            routeRepository.findRoutes(RouteRequest(leg.from.point, leg.to.point, mode))
        } catch (e: ApiException) {
            failed = true
            null
        }
    }
    val r: RouteResult? = result
    when {
        r != null -> GuideScreen(
            mode = mode,
            result = r,
            type = if (r.route(RouteType.SAFE) != null) RouteType.SAFE else r.routes.first().type,
            destinationName = if (last) destinationName else leg.to.name ?: "타는 곳",
            voiceByDefault = voiceByDefault,
            facilityRepository = facilityRepository,
            guardians = guardians,
            showTurnSigns = showTurnSigns,
            showSos = showSos,
            routeRepository = routeRepository,
            walkSpeedRepository = walkSpeedRepository,
            onFinish = onDone,
            finishLabel = finishLabel,
        )
        else -> Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(20.dp),
        ) {
            Text(walkTitle(leg, last, destinationName), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
            Text(
                if (failed) "걷는 길을 그리지 못했어요. ${leg.distanceM}m, 약 ${leg.minutes}분 걸어요." else "걷는 길을 찾는 중…",
                fontSize = 17.sp, color = Muted,
            )
            if (failed) {
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(finishLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** A bus or subway ride: what to take, where to get off, and a warning when that stop is near. */
@Composable
private fun RidePart(leg: TransitLeg, voiceByDefault: Boolean, nextLabel: String?, onDone: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val tts = rememberTts(context)
    LaunchedEffect(Unit) {
        // A moment for the speech engine to be ready.
        kotlinx.coroutines.delay(600)
        if (voiceByDefault) tts.speak(rideSpoken(leg), TextToSpeech.QUEUE_FLUSH, null, "ride")
    }
    // How far the stop to get off at is, from the phone's position while riding.
    var toStopM by remember { mutableStateOf<Int?>(null) }
    if (hasLocationPermission(context)) LocationUpdates(context) { here: LatLon ->
        toStopM = distanceMeters(here.lat, here.lon, leg.to.point.lat, leg.to.point.lon)
    }
    val near = toStopM?.let { it <= ALIGHT_NEAR_M } == true
    var warned by remember { mutableStateOf(false) }
    LaunchedEffect(near) {
        if (near && !warned) {
            warned = true
            if (voiceByDefault) tts.speak("곧 ${leg.to.name ?: "내릴 곳"}이에요. 내릴 준비를 하세요.", TextToSpeech.QUEUE_FLUSH, null, "alight")
        }
    }

    val color = if (leg.type == LegType.BUS) Blue else Color(0xFF6D28D9)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (leg.type == LegType.BUS) Icons.Filled.DirectionsBus else Icons.Filled.Subway,
                contentDescription = null, tint = color, modifier = Modifier.size(36.dp),
            )
            Text(rideTitle(leg), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink, lineHeight = 31.sp)
        }
        Text(rideDetail(leg), fontSize = 19.sp, color = Ink, lineHeight = 27.sp)
        Text("약 ${leg.minutes}분 타요", fontSize = 16.sp, color = Muted)
        nextRideText(leg)?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color) }
        if (near) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF4E5)) {
                Text(
                    "곧 ${leg.to.name ?: "내릴 곳"}이에요. 내릴 준비를 하세요.",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A5310),
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else toStopM?.let { m ->
            Text("내릴 곳까지 직선 ${if (m < 1000) "${m}m" else "%.1fkm".format(m / 1000.0)}", fontSize = 15.sp, color = Muted)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        ) { Text(if (nextLabel != null) "내렸어요 · $nextLabel" else "내렸어요 · 끝내기", fontSize = 18.sp, fontWeight = FontWeight.Black) }
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("안내 끝내기", fontSize = 16.sp)
        }
    }
}
