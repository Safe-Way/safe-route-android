package com.deungbul.anshimgil.ui.route

import com.deungbul.anshimgil.data.withEulReul
import com.deungbul.anshimgil.data.firstWait
import com.deungbul.anshimgil.data.FirstWait
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import com.deungbul.anshimgil.map.showTransit
import com.deungbul.anshimgil.data.nextTrainLines
import com.deungbul.anshimgil.data.nextBusText
import com.deungbul.anshimgil.data.legTitle
import com.deungbul.anshimgil.data.LegType
import com.deungbul.anshimgil.data.CompareItem
import com.deungbul.anshimgil.data.RouteChoice
import com.deungbul.anshimgil.data.choiceCounts
import com.deungbul.anshimgil.data.TransitLeg
import com.deungbul.anshimgil.data.TransitRoute
import com.deungbul.anshimgil.data.TransitResult
import com.deungbul.anshimgil.data.TransitRepository
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deungbul.anshimgil.data.ALL_CONDITION_KEYS
import com.deungbul.anshimgil.data.Condition
import com.deungbul.anshimgil.data.ApiException
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.Origin
import com.deungbul.anshimgil.data.Place
import com.deungbul.anshimgil.data.OriginIssue
import com.deungbul.anshimgil.location.LOCATION_PERMISSIONS
import com.deungbul.anshimgil.location.phoneOrigin
import com.deungbul.anshimgil.location.hasLocationPermission
import com.deungbul.anshimgil.data.RouteOption
import com.deungbul.anshimgil.data.RouteRepository
import com.deungbul.anshimgil.data.RouteRequest
import com.deungbul.anshimgil.data.RouteResult
import com.deungbul.anshimgil.data.routeCriteria
import com.deungbul.anshimgil.data.RouteType
import com.deungbul.anshimgil.data.eventLine
import com.deungbul.anshimgil.data.routeSpoken
import com.deungbul.anshimgil.data.transitSpoken
import kotlinx.coroutines.delay
import com.deungbul.anshimgil.data.walkMinutes
import com.deungbul.anshimgil.data.autoNightReason
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.map.KakaoMapView
import com.deungbul.anshimgil.map.showPoints
import com.deungbul.anshimgil.map.showRoutes
import com.deungbul.anshimgil.data.PlaceRepository
import com.deungbul.anshimgil.data.brandOf
import com.deungbul.anshimgil.data.nearestSameBrand
import com.deungbul.anshimgil.ui.mode.style
import com.kakao.vectormap.KakaoMap
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF17211B)
private val Muted = Color(0xFF4A564E)
private val Green = Color(0xFF1F6B42)

private sealed interface RouteState {
    data object Loading : RouteState
    data class Ready(val result: RouteResult) : RouteState
    /** [retryHelps] is false when trying again gives the same answer, e.g. start and destination too close. */
    data class Failed(val message: String, val retryHelps: Boolean, val code: String? = null) : RouteState
}

/** A nearer place of the same brand, looked up when the destination is too far to walk. */
private sealed interface Nearer {
    data object Looking : Nearer
    data object None : Nearer
    data class Found(val place: Place) : Nearer
}

/**
 * Shortest vs safe route with the difference in numbers (issue #6, FR-06·07).
 * The night chip switches night routing off and on, also in daytime (FR-03).
 * [startPlace] is a searched starting point; null starts at the phone's position.
 */
@Composable
fun RouteScreen(
    mode: UserMode,
    startPlace: Place?,
    destination: Place,
    routeRepository: RouteRepository,
    placeRepository: PlaceRepository,
    autoConditions: Boolean,
    onBack: () -> Unit,
    onPickStart: () -> Unit,
    onSwap: (Origin) -> Unit,
    onChangeDestination: (Place) -> Unit,
    /** The walker's own pace (see personalSpeed); route times are worked out from it. */
    walkSpeedMPerMin: Double,
    onStart: (RouteResult, RouteType) -> Unit,
    modifier: Modifier = Modifier,
    /** 대중교통 tab: guide the chosen transit route step by step. */
    onStartTransit: (TransitRoute) -> Unit = {},
    /** Open on the 대중교통 tab. */
    startWithTransit: Boolean = false,
    /** The kind of route the walker keeps choosing (쓰는 대로 맞추기), shown first. */
    preferred: RouteChoice? = null,
    /** Start guidance as soon as the route is found ([바로 출발] on a suggestion). */
    autoStart: Boolean = false,
    /** Which kind was started, to learn from. */
    onChosen: (RouteChoice) -> Unit = {},
    /** 음성 안내: read out the recommendation when it is found (사용성 점검표 17번). */
    voiceOn: Boolean = false,
) {
    val context = LocalContext.current
    var conditionsOff by rememberSaveable { mutableStateOf(if (autoConditions) emptySet() else ALL_CONDITION_KEYS) }
    var conditionsOn by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selected by rememberSaveable { mutableStateOf(if (preferred == RouteChoice.SHORTEST) RouteType.SHORTEST else RouteType.SAFE) }
    var state by remember { mutableStateOf<RouteState>(RouteState.Loading) }
    var retry by remember { mutableIntStateOf(0) }
    var map by remember { mutableStateOf<KakaoMap?>(null) }
    var origin by remember { mutableStateOf<Origin?>(null) }

    // Ask for location once when starting from it; without it the route starts at the demo point.
    var asked by remember { mutableStateOf(startPlace != null || hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { asked = true }
    LaunchedEffect(Unit) { if (!asked) permissionLauncher.launch(LOCATION_PERMISSIONS) }

    LaunchedEffect(asked, conditionsOff, conditionsOn, retry) {
        if (!asked) return@LaunchedEffect
        state = RouteState.Loading
        val from = (startPlace?.let { Origin(LatLon(it.lat, it.lon), it.name) }
            ?: phoneOrigin(context)).also { origin = it }
        state = try {
            RouteState.Ready(
                routeRepository.findRoutes(
                    RouteRequest(from.point, LatLon(destination.lat, destination.lon), mode, conditionsOff, conditionsOn),
                ),
            )
        } catch (e: ApiException) {
            RouteState.Failed(e.message, retryHelps = e.code !in NO_RETRY_CODES, code = e.code)
        }
    }

    // Too far: look for the nearest branch of the same place (e.g. another Starbucks) to walk to instead.
    var nearer by remember { mutableStateOf<Nearer?>(null) }
    LaunchedEffect(state) {
        nearer = null
        val from = origin ?: return@LaunchedEffect
        if ((state as? RouteState.Failed)?.code != "TOO_FAR") return@LaunchedEffect
        nearer = Nearer.Looking
        nearer = try {
            val results = placeRepository.search(brandOf(destination.name), from.point.lat, from.point.lon)
            nearestSameBrand(destination, results)?.let { Nearer.Found(it) } ?: Nearer.None
        } catch (e: ApiException) {
            Nearer.None
        }
    }

    // No route: still show where the start and the destination (or the nearer place) are.
    LaunchedEffect(map, state, nearer) {
        val m = map ?: return@LaunchedEffect
        val from = origin ?: return@LaunchedEffect
        val dest = LatLon(destination.lat, destination.lon)
        when ((state as? RouteState.Failed)?.code) {
            "SAME_POINT" -> m.showPoints(context, from.point, listOf(dest to destination.name), focus = listOf(dest))
            "TOO_FAR" -> when (val n = nearer) {
                is Nearer.Found -> {
                    val near = LatLon(n.place.lat, n.place.lon)
                    m.showPoints(context, from.point, listOf(near to n.place.name, dest to destination.name), focus = listOf(near))
                }
                Nearer.None -> m.showPoints(context, from.point, listOf(dest to destination.name), focus = listOf(dest))
                else -> Unit
            }
        }
    }
    // After the user turns location on (or allows it) in the phone's settings, find the start again.
    var sentToSettings by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sentToSettings) {
                sentToSettings = false
                asked = true
                retry++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun fixLocation(issue: OriginIssue) {
        when (issue) {
            OriginIssue.LOCATION_OFF -> {
                sentToSettings = true
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            OriginIssue.NO_PERMISSION -> {
                sentToSettings = true
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }
            OriginIssue.NO_FIX -> retry++
            OriginIssue.OUTSIDE_GWANGJU -> Unit
        }
    }

    val result = (state as? RouteState.Ready)?.result

    // Evening or rain: switch night routing on by itself, unless the user set the night chip or turned
    // automatic conditions off in settings. The reason shows under the chips.
    var nightTouched by rememberSaveable { mutableStateOf(false) }
    var autoNightNote by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(result) {
        val r = result ?: return@LaunchedEffect
        if (!autoConditions || nightTouched || "night" in conditionsOff || "night" in conditionsOn) return@LaunchedEffect
        autoNightReason(r, java.time.LocalTime.now())?.let { reason ->
            autoNightNote = "$reason 밤 기준을 켰어요"
            conditionsOn = conditionsOn + "night"
        }
    }
    // Read out what is recommended, once per result and tab, when 음성 안내 is on.
    val tts = com.deungbul.anshimgil.ui.guide.rememberTts(context)
    // 걸어서 / 대중교통 tabs. Transit is asked for only when its tab is opened (the server's ODsay
    // lookups are limited per day) and kept while the screen is open.
    var transitTab by rememberSaveable { mutableStateOf(startWithTransit || preferred == RouteChoice.TRANSIT) }
    // What this screen picked before the walker did: going along with it says nothing ([choiceCounts]).
    val preselected = remember {
        when {
            startWithTransit || preferred == RouteChoice.TRANSIT -> RouteChoice.TRANSIT
            else -> preferred
        }
    }
    fun chosen(choice: RouteChoice) { if (choiceCounts(preselected, choice)) onChosen(choice) }
    var transit by remember { mutableStateOf<TransitState?>(null) }
    var transitSelected by rememberSaveable { mutableIntStateOf(0) }
    // [바로 출발]: start as soon as the route (of the usual kind) is there; the walker can still go back.
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoStart, state, transit, transitTab) {
        if (!autoStart || autoStarted) return@LaunchedEffect
        if (transitTab) {
            (transit as? TransitState.Ready)?.result?.routes?.firstOrNull()?.let { autoStarted = true; onStartTransit(it) }
        } else {
            (state as? RouteState.Ready)?.let { s ->
                val type = if (s.result.route(selected) != null) selected else s.result.routes.first().type
                autoStarted = true
                onStart(s.result, type)
            }
        }
    }
    LaunchedEffect(result, transit, transitTab, selected) {
        if (!voiceOn) return@LaunchedEffect
        val speaker = tts
        val text = if (transitTab) (transit as? TransitState.Ready)?.result?.routes?.getOrNull(transitSelected)?.let(::transitSpoken)
        else result?.let { routeSpoken(it, selected) }
        if (text != null) {
            delay(800) // let the screen and the speech engine settle first
            speaker.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "route")
        }
    }
    LaunchedEffect(transitTab, origin) {
        val from = origin ?: return@LaunchedEffect
        if (!transitTab || transit is TransitState.Ready) return@LaunchedEffect
        transit = TransitState.Loading
        transit = try {
            val r = TransitRepository.find(from.point, LatLon(destination.lat, destination.lon), mode, routeRepository.guestId())
            if (r.routes.isEmpty()) TransitState.Failed(r.note ?: "대중교통 경로가 없어요", null) else TransitState.Ready(r)
        } catch (e: ApiException) {
            TransitState.Failed(e.message, e.code)
        }
    }

    LaunchedEffect(map, result, selected, transitTab, transit, transitSelected) {
        val m = map ?: return@LaunchedEffect
        val t = (transit as? TransitState.Ready)?.result
        if (transitTab && t != null) {
            t.routes.getOrNull(transitSelected)?.let { m.showTransit(context, it, destination.name) }
        } else if (!transitTab) {
            result?.let { m.showRoutes(context, it, selected, destination.name) }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            KakaoMapView(modifier = Modifier.fillMaxSize(), onMapReady = { map = it })
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 4.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 10.dp).heightIn(min = 56.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기", tint = Ink)
                        }
                        // Tap the start to search for another one.
                        Text(
                            origin?.label ?: startPlace?.name ?: "현재 위치", fontSize = 15.sp, color = Muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(onClickLabel = "출발지 바꾸기", onClick = onPickStart)
                                .padding(vertical = 8.dp),
                        )
                        IconButton(onClick = { origin?.let(onSwap) }, enabled = origin != null) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "출발과 도착 바꾸기", tint = Ink)
                        }
                        Text(
                            destination.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        val style = mode.style()
                        Surface(shape = RoundedCornerShape(12.dp), color = style.accent, contentColor = Color.White) {
                            Text(
                                "${style.label} 모드", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                // Why the route starts at Chosun University instead of here, with a way to fix it.
                origin?.issue?.takeIf { startPlace == null }?.let { issue ->
                    LocationNotice(issue, onFix = { fixLocation(issue) })
                }
                // Night is always shown so it can also be switched on in daytime; weather is shown for information.
                result?.let { r -> r.conditions.ifEmpty { listOf(Condition("night", "밤", false)) } }?.let { conditions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Ink, contentColor = Color.White) {
                            Text("지금", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        conditions.forEach { condition ->
                            FilterChip(
                                selected = condition.on,
                                onClick = {
                                    val key = condition.key
                                    if (key == "night") {
                                        nightTouched = true
                                        autoNightNote = null
                                    }
                                    if (condition.on) {
                                        conditionsOff = conditionsOff + key
                                        conditionsOn = conditionsOn - key
                                    } else {
                                        conditionsOff = conditionsOff - key
                                        conditionsOn = conditionsOn + key
                                    }
                                },
                                label = { Text("${condition.label} 기준 " + if (condition.on) "켜짐" else "꺼짐", fontSize = 14.sp) },
                                // Solid background so the text stays readable over the map.
                                colors = FilterChipDefaults.filterChipColors(containerColor = Color.White),
                                modifier = Modifier.heightIn(min = 40.dp),
                            )
                        }
                    }
                }
                autoNightNote?.let { note ->
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 2.dp) {
                        Text(note, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                result?.weather?.takeIf { it.isNotEmpty() }?.let { notes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        notes.forEach { note ->
                            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 2.dp) {
                                Text(note, fontSize = 14.sp, color = Ink, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }
        }

        // At most a bit over half the screen so the map stays visible with large text;
        // the details scroll while the start button stays in view.
        val maxPanel = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
        Surface(shadowElevation = 8.dp, color = Color.White, modifier = Modifier.heightIn(max = maxPanel)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                ModeTabs(transitTab, onSelect = { transitTab = it })
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (transitTab) {
                        TransitPanel(transit, transitSelected, onSelect = { transitSelected = it }, onWalk = { transitTab = false })
                    } else when (val s = state) {
                        RouteState.Loading -> Text(if (origin == null) "현재 위치를 찾는 중…" else "길을 찾는 중…", fontSize = 16.sp, color = Muted, modifier = Modifier.padding(vertical = 24.dp))
                        is RouteState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.message, fontSize = 16.sp, color = Color(0xFFB42318))
                            when {
                                s.code == "SAME_POINT" -> {
                                    Text("바로 옆이에요. 지도에서 위치를 확인하세요.", fontSize = 15.sp, color = Muted)
                                    TextButton(onClick = onPickStart) { Text("출발지 바꾸기") }
                                }
                                s.code == "TOO_FAR" -> {
                                    Button(
                                        onClick = { transitTab = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F5BB7)),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    ) { Text("대중교통으로 가기", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                                    NearerChoice(nearer, onGo = onChangeDestination, onSearch = onBack)
                                }
                                s.retryHelps -> TextButton(onClick = { retry++ }) { Text("다시 시도") }
                                else -> TextButton(onClick = onPickStart) { Text("출발지 바꾸기") }
                            }
                        }
                        is RouteState.Ready -> RouteDetails(s.result.atPace(walkSpeedMPerMin), mode, selected, onSelect = { selected = it })
                    }
                }
                if (!transitTab) (state as? RouteState.Ready)?.let { s ->
                    StartButton(s.result.atPace(walkSpeedMPerMin), selected, onStart = {
                        if (s.result.routes.size > 1) chosen(if (selected == RouteType.SHORTEST) RouteChoice.SHORTEST else RouteChoice.SAFE)
                        onStart(s.result, selected)
                    })
                }
                if (transitTab) (transit as? TransitState.Ready)?.result?.routes?.getOrNull(transitSelected)?.let { t ->
                    Button(
                        onClick = { chosen(RouteChoice.TRANSIT); onStartTransit(t) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F5BB7), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    ) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("대중교통으로 안내 시작", fontSize = 19.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/** Trying again gives the same answer for these, so the panel offers something else. */
private val NO_RETRY_CODES = setOf("SAME_POINT", "TOO_FAR", "OUT_OF_AREA", "BAD_PARAM")

/** For a destination too far to walk: the nearest place of the same brand, or another search. */
@Composable
private fun NearerChoice(nearer: Nearer?, onGo: (Place) -> Unit, onSearch: () -> Unit) {
    when (nearer) {
        is Nearer.Found -> {
            val place = nearer.place
            Text("가장 가까운 같은 곳이에요. 누르면 이곳으로 길을 찾아요.", fontSize = 15.sp, color = Muted)
            Button(
                onClick = { onGo(place) },
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                val distance = place.distanceM?.let { if (it < 1000) " (${it}m)" else " (%.1fkm)".format(Locale.KOREA, it / 1000.0) } ?: ""
                Text("${place.name}$distance", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Nearer.Looking -> Text("가까운 곳을 찾는 중…", fontSize = 15.sp, color = Muted)
        else -> Text("걸어서 갈 만한 거리에 같은 곳이 없어요", fontSize = 15.sp, color = Muted)
    }
    TextButton(onClick = onSearch) { Text("다른 곳 찾기") }
}

@Composable
private fun LocationNotice(issue: OriginIssue, onFix: () -> Unit) {
    val action = when (issue) {
        OriginIssue.LOCATION_OFF -> "위치 켜기"
        OriginIssue.NO_PERMISSION -> "권한 허용"
        OriginIssue.NO_FIX -> "다시 찾기"
        OriginIssue.OUTSIDE_GWANGJU -> null
    }
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF4E5), shadowElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 48.dp),
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color(0xFFB45309))
            Text(issue.message, fontSize = 14.sp, color = Color(0xFF7A5310), lineHeight = 19.sp, modifier = Modifier.weight(1f))
            action?.let { TextButton(onClick = onFix) { Text(it, fontSize = 15.sp, fontWeight = FontWeight.Bold) } }
        }
    }
}

@Composable
private fun RouteDetails(
    result: RouteResult,
    mode: UserMode,
    selected: RouteType,
    onSelect: (RouteType) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
        listOf(RouteType.SAFE, RouteType.SHORTEST).forEach { type ->
            result.route(type)?.let { route ->
                RouteCard(
                    route, selected = type == selected, only = result.routes.size == 1,
                    onClick = { onSelect(type) }, modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Why this route, in one line; 자세히 opens what the route engine weighs for this mode and time.
    val night = result.conditions.any { it.key == "night" && it.on }
    val criteria = routeCriteria(mode, night)
    val (covered, items) = result.explanation.items
        .filter { it.key.isEmpty() || it.key in criteria.counts }
        .partition { it.key.startsWith("covered.") }
    var more by rememberSaveable { mutableStateOf(false) }
    // Only the chosen route's facts, so two sets of numbers do not mix (11/6).
    val fast = selected == RouteType.SHORTEST && result.routes.size > 1
    fun valueOf(item: CompareItem) = if (fast) item.shortest else item.safe
    val fastSummary = run {
        val s = result.route(RouteType.SAFE)
        val f = result.route(RouteType.SHORTEST)
        if (s == null || f == null) null else {
            val m = (s.distanceM - f.distanceM).coerceAtLeast(0)
            val min = (s.durationMin - f.durationMin).coerceAtLeast(0)
            if (m > 0) "안전한 길보다 ${m}m(${min}분) 짧아요." else null
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEEF6F0), RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (fast) "가장 빠른 길이에요." else criteria.short, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 24.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { more = !more }) { Text(if (more) "접기" else "자세히", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
        // The server's real difference, e.g. "24m(1분) 더 돌지만 쉼 곳 +1곳".
        (if (fast) fastSummary else result.explanation.summary.takeIf { it.isNotBlank() })?.let {
            Text(it, fontSize = 15.sp, color = Muted, lineHeight = 21.sp, modifier = Modifier.padding(end = 8.dp))
        }
        // Warnings and notices stay in view: stairs that could not be avoided, an event on the way.
        // The server's notes are about the safe route.
        if (!fast) result.explanation.notes.forEach { note ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    if (note.warn) Icons.Filled.WarningAmber else Icons.Filled.Info,
                    contentDescription = if (note.warn) "주의" else "알림",
                    tint = if (note.warn) Color(0xFFB45309) else Color(0xFF2F5BB7),
                    modifier = Modifier.size(20.dp),
                )
                Text(note.text, fontSize = 15.sp, color = if (note.warn) Color(0xFF7A5310) else Ink, lineHeight = 21.sp)
            }
        }
        // Events and incidents on this route (팀 할 일 m08): a caution when this mode keeps away from them.
        result.route(if (fast) RouteType.SHORTEST else RouteType.SAFE)?.events.orEmpty().forEach { e ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    if (e.avoid) Icons.Filled.WarningAmber else Icons.Filled.Info,
                    contentDescription = if (e.avoid) "주의" else "알림",
                    tint = if (e.avoid) Color(0xFFB45309) else Color(0xFF2F5BB7),
                    modifier = Modifier.size(20.dp),
                )
                Text(eventLine(e), fontSize = 15.sp, color = if (e.avoid) Color(0xFF7A5310) else Ink, lineHeight = 21.sp)
            }
        }
        if (items.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 8.dp)) {
                items.forEach { item ->
                    Row {
                        Text(item.label, fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f))
                        Text("${valueOf(item)}${item.unit}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                }
                Text(if (fast) "빠른 길 주변" else "안전한 길 주변", fontSize = 12.sp, color = Muted)
            }
        }
        if (more) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)) {
                Text("${mode.style().label} 모드 · ${if (night) "밤" else "낮"}에 안전한 길을 고르는 기준", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Muted)
                criteria.details.forEach { Text("· $it", fontSize = 15.sp, color = Ink, lineHeight = 21.sp) }
                covered.forEach { item ->
                    Row(Modifier.padding(top = 4.dp)) {
                        Text(item.label, fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f))
                        Text("${valueOf(item)}${item.unit}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                }
            }
        } else if (items.isEmpty()) {
            Spacer(Modifier.height(4.dp))
        }
    }

    if (!result.dataQuality.ok) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color(0xFFB45309))
            Text(
                result.dataQuality.message ?: "이 지역은 안전 시설 데이터가 부족해요.",
                fontSize = 14.sp, color = Color(0xFF7A5310),
            )
        }
    }
}

@Composable
private fun StartButton(result: RouteResult, selected: RouteType, onStart: () -> Unit) {
    Button(
        onClick = onStart,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
    ) {
        Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                result.routes.size == 1 -> "길 안내 시작"
                selected == RouteType.SAFE -> "안전한 길로 안내 시작"
                else -> "빠른 길로 안내 시작"
            },
            fontSize = 19.sp, fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun RouteCard(route: RouteOption, selected: Boolean, only: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val safe = route.type == RouteType.SAFE
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color.White else Color(0xFFF4F7F3),
        border = BorderStroke(3.dp, if (selected) Ink else Color.Transparent),
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .width(18.dp)
                        .heightIn(min = 5.dp)
                        .background(if (safe) Green else Color(0xFF6B776F), RoundedCornerShape(3.dp)),
                )
                Text(
                    when {
                        only -> "안내할 길"
                        safe -> "안전한 길 · 추천"
                        else -> "가장 빠른 길"
                    },
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (safe) Green else Muted,
                )
            }
            Text("${route.durationMin}분", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Ink)
            Text(formatDistance(route.distanceM), fontSize = 14.sp, color = Muted)
            Text("도착 ${arrivalTime(route.durationMin)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
    }
}

private val ARRIVAL_FORMAT = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

/** Clock time after walking [minutes] from now, e.g. "오전 9:48". */
internal fun arrivalTime(minutes: Int): String = LocalTime.now().plusMinutes(minutes.toLong()).format(ARRIVAL_FORMAT)

internal fun formatDistance(meters: Int): String =
    if (meters < 1000) "${meters}m" else "%.1fkm".format(meters / 1000.0)

/** The same routes with walking times from the walker's own pace instead of the mode's default. */
private fun RouteResult.atPace(speedMPerMin: Double): RouteResult =
    copy(routes = routes.map { it.copy(durationMin = walkMinutes(it.distanceM.toDouble(), speedMPerMin)) })

private sealed interface TransitState {
    data object Loading : TransitState
    data class Ready(val result: TransitResult) : TransitState
    data class Failed(val message: String, val code: String?) : TransitState
}

private val Transit = Color(0xFF2F5BB7)

/** 걸어서 / 대중교통 switch at the top of the route panel. */
@Composable
private fun ModeTabs(transit: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFEFF3EE), RoundedCornerShape(12.dp))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        listOf(false to "걸어서", true to "대중교통").forEach { (isTransit, label) ->
            val on = transit == isTransit
            Text(
                label,
                fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = if (on) Color.White else Ink,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) (if (isTransit) Transit else Green) else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(isTransit) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

/** Up to three transit routes; the chosen one shows its steps and is drawn on the map. */
@Composable
private fun TransitPanel(state: TransitState?, selected: Int, onSelect: (Int) -> Unit, onWalk: () -> Unit) {
    when (state) {
        null, TransitState.Loading -> Text("대중교통 길을 찾는 중…", fontSize = 16.sp, color = Muted, modifier = Modifier.padding(vertical = 24.dp))
        is TransitState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val text = when (state.code) {
                "TOO_CLOSE" -> "가까워서 걸어가는 게 나아요"
                "DAILY_LIMIT" -> "오늘 대중교통 길찾기 횟수를 다 썼어요. 내일 다시 해 주세요"
                "NO_ROUTE" -> "대중교통으로 가는 길이 없어요"
                else -> state.message
            }
            Text(text, fontSize = 16.sp, color = Color(0xFFB42318))
            TextButton(onClick = onWalk) { Text("걸어서 가는 길 보기") }
        }
        is TransitState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.result.note?.let { Text(it, fontSize = 14.sp, color = Muted) }
            state.result.routes.forEachIndexed { i, route ->
                TransitCard(route, selected = i == selected, onClick = { onSelect(i) })
            }
        }
    }
}

@Composable
private fun TransitCard(route: TransitRoute, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFFEFF4FB) else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Transit else Color(0xFFDCE3DD)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // With the wait at the first stop, so the arrival time is when the walker really gets there.
            val wait = firstWait(route)
            val total = route.totalMin + (wait?.waitMin ?: 0)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${total}분", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink)
                Text("도착 ${arrivalTime(total)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
            wait?.takeIf { it.waitMin > 0 }?.let { Text("기다리는 시간 약 ${it.waitMin}분 포함", fontSize = 13.sp, color = Transit) }
            Text(
                listOfNotNull(
                    route.fare?.let { "%,d원".format(it) },
                    "걷기 ${formatDistance(route.walkM)}",
                    if (route.transfers == 0) "환승 없음" else "환승 ${route.transfers}번",
                ).joinToString(" · "),
                fontSize = 14.sp, color = Muted,
            )
            // Steps only for the chosen route, so the list stays short.
            if (selected) route.legs.forEachIndexed { i, leg -> LegRow(leg, wait?.takeIf { it.legIndex == i }) }
        }
    }
}

@Composable
private fun LegRow(leg: TransitLeg, wait: FirstWait?) {
    val (mark, color) = when (leg.type) {
        LegType.WALK -> "걷기" to Green
        LegType.BUS -> "버스" to Transit
        LegType.SUBWAY -> "지하철" to Color(0xFF6D28D9)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(
            mark, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.background(color, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(legTitle(leg), fontSize = 15.sp, color = Ink, lineHeight = 20.sp)
            val live = when (leg.type) {
                LegType.BUS -> nextBusText(leg.buses)
                LegType.SUBWAY -> nextTrainLines(leg.trains).firstOrNull()
                LegType.WALK -> null
            }
            live?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color) }
            wait?.let {
                Text(
                    if (it.waitMin == 0) "도착하면 바로 ${withEulReul(it.catches)} 탈 수 있어요" else "정류장에서 약 ${it.waitMin}분 기다려 ${withEulReul(it.catches)} 타요",
                    fontSize = 14.sp, color = Muted,
                )
            }
        }
    }
}
