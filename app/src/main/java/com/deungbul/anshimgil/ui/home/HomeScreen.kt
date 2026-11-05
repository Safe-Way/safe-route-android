package com.deungbul.anshimgil.ui.home

import com.deungbul.anshimgil.data.stationExits
import com.deungbul.anshimgil.data.FacilityKind
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import com.deungbul.anshimgil.data.Facility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deungbul.anshimgil.data.ApiException
import com.deungbul.anshimgil.data.Bbox
import com.deungbul.anshimgil.data.FacilityRepository
import com.deungbul.anshimgil.data.Place
import com.deungbul.anshimgil.data.PlaceRepository
import com.deungbul.anshimgil.data.UserMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.chooseOrigin
import com.deungbul.anshimgil.location.LOCATION_PERMISSIONS
import com.deungbul.anshimgil.location.LocationUpdates
import com.deungbul.anshimgil.location.hasLocationPermission
import com.deungbul.anshimgil.location.lastKnownLocation
import com.deungbul.anshimgil.location.phoneOrigin
import com.deungbul.anshimgil.map.KakaoMapView
import com.deungbul.anshimgil.map.addMyLocation
import com.deungbul.anshimgil.map.pointTo
import com.deungbul.anshimgil.map.tiltForWalking
import com.deungbul.anshimgil.location.rememberCompassHeading
import com.deungbul.anshimgil.map.showLayers
import com.kakao.vectormap.label.Label
import com.deungbul.anshimgil.ui.common.MapMessage
import com.deungbul.anshimgil.ui.mode.style
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kakao.vectormap.camera.CameraUpdateFactory

private val Ink = Color(0xFF17211B)

/** Same scale as the guide screen: nearby streets with buildings in 3D, one step closer than the default 16. */
private const val WALK_ZOOM = 17

/** One-tap shortcuts: the server's `kind`, button text and icon. */
private val NEARBY = listOf(
    Triple("store", "편의점", Icons.Filled.Store),
    Triple("police", "파출소", Icons.Filled.LocalPolice),
    Triple("toilet", "화장실", Icons.Filled.Wc),
)

/**
 * Home map (issue #3): a plain 2D map that shows only what the chosen mode needs,
 * plus buttons that find the route to the nearest store, police box or toilet.
 */
@Composable
fun HomeScreen(
    mode: UserMode,
    extraMapItems: Set<String>,
    facilityRepository: FacilityRepository,
    placeRepository: PlaceRepository,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlaceSelected: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var map by remember { mutableStateOf<KakaoMap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf<String?>(null) }
    var nearbyMessage by remember { mutableStateOf<String?>(null) }
    var bbox by remember { mutableStateOf<Bbox?>(null) }
    var mapNote by remember { mutableStateOf<String?>(null) }
    // The tapped place whose details are shown, e.g. a station's elevator exits.
    var picked by remember { mutableStateOf<Facility?>(null) }
    // What the map shows now, so a station's card can list its exits.
    var shownFacilities by remember { mutableStateOf<List<Facility>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // The phone's position, kept up to date so the location button moves at once and the dot follows.
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var here by remember { mutableStateOf(lastKnownLocation(context)) }
    val startCenter = remember { chooseOrigin(here).point.let { LatLng.from(it.lat, it.lon) } }
    var me by remember { mutableStateOf<Label?>(null) }
    var locating by remember { mutableStateOf(false) }
    if (hasPermission) LocationUpdates(context) { here = it }
    // Whether the next fix should also move the map (pressed while the position was still unknown).
    var centerOnFix by remember { mutableStateOf(false) }

    fun centerOn(p: LatLon) {
        map?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(p.lat, p.lon), WALK_ZOOM))
    }

    fun locate() {
        here?.let { centerOn(it); nearbyMessage = null; return }
        if (locating) return
        locating = true
        centerOnFix = true
        nearbyMessage = "현재 위치를 찾는 중이에요…"
        scope.launch {
            val origin = phoneOrigin(context, timeoutMs = 6_000)
            locating = false
            // A fix that arrived through the updates meanwhile has already moved the map.
            if (!centerOnFix) return@launch
            centerOnFix = false
            if (origin.fromGps) here = origin.point
            // Say why the map went to Chosun University instead of the phone's position.
            nearbyMessage = origin.issue?.message?.replace("에서 출발해요", "을 보여 줘요")
            centerOn(origin.point)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = hasLocationPermission(context)
        locate()
    }

    LaunchedEffect(map, here) {
        val m = map ?: return@LaunchedEffect
        val p = here ?: return@LaunchedEffect
        if (centerOnFix) {
            centerOnFix = false
            nearbyMessage = null
            centerOn(p)
        }
        // Glide between fixes (they come about once a second) instead of jumping.
        me?.moveTo(LatLng.from(p.lat, p.lon), 900) ?: run { me = m.addMyLocation(context, p) }
    }
    // The arrow on the dot turns with the phone (compass).
    val heading = rememberCompassHeading()
    LaunchedEffect(me, heading) { heading?.let { me?.pointTo(it, 300) } }

    // The server picks the place: within 1 km, open ones first, narrowed by the mode.
    fun goToNearest(kind: String, label: String) {
        if (finding != null) return
        finding = kind
        nearbyMessage = null
        scope.launch {
            // Outside Gwangju the server has no places, so the demo point is used as for routes.
            val from = here?.let { chooseOrigin(it).point } ?: phoneOrigin(context).point
            try {
                val result = placeRepository.nearby(kind, mode, from.lat, from.lon)
                val nearest = result.places.firstOrNull()
                if (nearest != null) onPlaceSelected(nearest)
                else nearbyMessage = result.message ?: "가까운 ${label}을(를) 찾지 못했어요"
            } catch (e: ApiException) {
                nearbyMessage = e.message
            }
            finding = null
        }
    }

    // Load facilities for the visible area whenever the map stops moving.
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        m.tiltForWalking()
        bbox = m.visibleBbox()
        m.setOnCameraMoveEndListener { moved, _, _ -> moved.visibleBbox()?.let { bbox = it } }
        m.setOnLabelClickListener { _, _, label ->
            (label.tag as? Facility)?.let { picked = it }
            true
        }
    }
    LaunchedEffect(map, mode, extraMapItems, bbox) {
        val m = map ?: return@LaunchedEffect
        val area = bbox ?: return@LaunchedEffect
        delay(300) // wait for the map to settle after a drag
        mapNote = try {
            val page = facilityRepository.load(mode, extraMapItems, area)
            m.showLayers(context, page.layers)
            shownFacilities = page.layers.facilities
            // showLayers clears every label, the position dot included.
            me = here?.let { m.addMyLocation(context, it) }
            when {
                facilityRepository.isExample -> "시설 정보: 예시 데이터 (서버 연결 전)"
                page.truncated -> "시설이 많아 일부만 보여요. 확대하면 더 보여요"
                else -> null
            }
        } catch (e: ApiException) {
            // Kept short and distinct from the shortcut buttons' message, which may show at the same time.
            if (e.code == "NETWORK") "지도 시설을 불러오지 못했어요 (인터넷 연결 확인)" else e.message
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        KakaoMapView(
            modifier = Modifier.fillMaxSize(),
            // Open where the phone already is (in Gwangju), close enough to see the streets around.
            center = startCenter,
            zoomLevel = WALK_ZOOM,
            onMapReady = { map = it },
            onMapError = { error = "지도를 불러오지 못했어요. 키와 키 해시 등록을 확인해 주세요.\n(${it.message})" },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onOpenSearch,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF1F6B42))
                        Text("어디로 갈까요?", fontSize = 17.sp, color = Color(0xFF5E6A62))
                    }
                }
                Surface(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Tune, contentDescription = "설정", tint = Ink)
                    }
                }
            }
            val style = mode.style()
            Surface(shape = RoundedCornerShape(16.dp), color = style.accent, contentColor = Color.White) {
                Text(
                    "${style.label} 모드",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NEARBY.forEach { (query, label, icon) ->
                    NearbyButton(
                        text = if (finding == query) "찾는 중…" else label,
                        icon = icon,
                        onClick = { goToNearest(query, label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            nearbyMessage?.let {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 2.dp) {
                    Text(it, fontSize = 14.sp, color = Color(0xFFB42318), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }

        // The phone's position when it is known and in Gwangju, otherwise the demo area.
        Surface(
            onClick = { if (hasPermission) locate() else permissionLauncher.launch(LOCATION_PERMISSIONS) },
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 48.dp)
                .size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MyLocation, contentDescription = "내 위치로 이동", tint = Color(0xFF2F6FDB))
            }
        }

        mapNote?.let { note ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    note,
                    fontSize = 12.sp,
                    color = Color(0xFF4A564E),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        picked?.let { place ->
            PlaceDetailCard(
                place,
                exits = if (place.kind == FacilityKind.SUBWAY) stationExits(place, shownFacilities) else emptyList(),
                onClose = { picked = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 112.dp),
            )
        }

        error?.let { MapMessage(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun NearbyButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1F6B42), modifier = Modifier.size(22.dp))
            Column {
                Text("가까운", fontSize = 12.sp, color = Color(0xFF4A564E), maxLines = 1)
                Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
            }
        }
    }
}

/** The map area on screen, or null before the map has a size. */
private fun KakaoMap.visibleBbox(): Bbox? {
    val v = viewport
    if (v.width() <= 0 || v.height() <= 0) return null
    val a = fromScreenPoint(v.left, v.top) ?: return null
    val b = fromScreenPoint(v.right, v.bottom) ?: return null
    return Bbox(
        west = minOf(a.longitude, b.longitude), south = minOf(a.latitude, b.latitude),
        east = maxOf(a.longitude, b.longitude), north = maxOf(a.latitude, b.latitude),
    )
}

/**
 * Details of a tapped place, one item per line: for a station, which exits have elevators,
 * accessible toilets, wheelchair chargers and train hours, as the server words them.
 */
@Composable
private fun PlaceDetailCard(place: Facility, exits: List<String>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val parts = place.detail.orEmpty().split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
    // The first part repeats the kind ("지하철역"), so it goes next to the name instead.
    val kindText = parts.firstOrNull()?.takeIf { place.name != null && parts.size > 1 }
    val lines = if (kindText != null) parts.drop(1) else parts
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The kind ("지하철역") sits small above the name so a long station name keeps one line.
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    kindText?.let { Text(it, fontSize = 13.sp, color = Color(0xFF5E6A62)) }
                    Text(place.name ?: place.label, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Ink) }
            }
            lines.forEach { line -> Text("· $line", fontSize = 16.sp, color = Ink, lineHeight = 22.sp, modifier = Modifier.padding(end = 12.dp)) }
            // Each exit on the map with whether it has an elevator, so the walker knows which exit to use.
            if (exits.isNotEmpty()) {
                Text("출구", fontSize = 13.sp, color = Color(0xFF5E6A62), modifier = Modifier.padding(top = 8.dp))
                exits.forEach { exit ->
                    val withElevator = "엘리베이터 있음" in exit
                    Text(
                        exit, fontSize = 15.sp, lineHeight = 21.sp,
                        fontWeight = if (withElevator) FontWeight.Bold else FontWeight.Normal,
                        color = if (withElevator) Color(0xFF1F6B42) else Color(0xFF5E6A62),
                    )
                }
            }
        }
    }
}
