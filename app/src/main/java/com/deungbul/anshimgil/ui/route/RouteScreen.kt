package com.deungbul.anshimgil.ui.route

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
import com.deungbul.anshimgil.data.RouteType
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.map.KakaoMapView
import com.deungbul.anshimgil.map.showRoutes
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
    data class Failed(val message: String) : RouteState
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
    autoConditions: Boolean,
    onBack: () -> Unit,
    onPickStart: () -> Unit,
    onSwap: (Origin) -> Unit,
    onStart: (RouteResult, RouteType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var conditionsOff by rememberSaveable { mutableStateOf(if (autoConditions) emptySet() else ALL_CONDITION_KEYS) }
    var conditionsOn by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selected by rememberSaveable { mutableStateOf(RouteType.SAFE) }
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
            RouteState.Failed(e.message)
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
    LaunchedEffect(map, result, selected) {
        val m = map ?: return@LaunchedEffect
        result?.let { m.showRoutes(context, it, selected, destination.name) }
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "검색으로 돌아가기", tint = Ink)
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
                // Night is always shown so it can also be switched on in daytime.
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
            }
        }

        Surface(shadowElevation = 8.dp, color = Color.White) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                when (val s = state) {
                    RouteState.Loading -> Text(if (origin == null) "현재 위치를 찾는 중…" else "길을 찾는 중…", fontSize = 16.sp, color = Muted, modifier = Modifier.padding(vertical = 24.dp))
                    is RouteState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.message, fontSize = 16.sp, color = Color(0xFFB42318))
                        TextButton(onClick = { retry++ }) { Text("다시 시도") }
                    }
                    is RouteState.Ready -> RouteDetails(s.result, selected, onSelect = { selected = it }, onStart = { onStart(s.result, selected) })
                }
            }
        }
    }
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
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 44.dp),
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
    selected: RouteType,
    onSelect: (RouteType) -> Unit,
    onStart: () -> Unit,
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

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEEF6F0), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(result.explanation.summary, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 24.sp)
        result.explanation.items.forEach { item ->
            Row {
                Text(item.label, fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f))
                Text(
                    "${item.shortest}${item.unit} → ${item.safe}${item.unit}",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink,
                )
            }
        }
        if (result.explanation.items.isNotEmpty()) {
            Text("가장 빠른 길 → 안전한 길로 바꾸면", fontSize = 12.sp, color = Muted)
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
