package com.deungbul.anshimgil.ui.home

import com.deungbul.anshimgil.ui.common.AI_BUBBLE_ALPHA
import com.deungbul.anshimgil.data.busesToShow
import com.deungbul.anshimgil.data.BusRepository
import com.deungbul.anshimgil.data.NearbyStop
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.deungbul.anshimgil.data.nextTrainLines
import com.deungbul.anshimgil.data.matchStation
import com.deungbul.anshimgil.data.SubwayTrains
import com.deungbul.anshimgil.ui.safety.SosOverlay
import com.deungbul.anshimgil.ui.mode.ModeBadge
import com.deungbul.anshimgil.ui.safety.SosButton
import com.deungbul.anshimgil.data.Guardian
import com.deungbul.anshimgil.data.MapLayers
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.material.icons.filled.AutoAwesome
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
import com.deungbul.anshimgil.data.ShownHints
import com.deungbul.anshimgil.data.MapHintSource
import com.deungbul.anshimgil.data.MapHint
import com.deungbul.anshimgil.data.ButtonBox
import com.deungbul.anshimgil.data.MISS_NEAR_DP
import com.deungbul.anshimgil.data.MissDetector
import com.deungbul.anshimgil.data.TAP_MAX_MS
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import com.deungbul.anshimgil.data.nearbyOrder
import com.deungbul.anshimgil.data.Adaptation
import com.deungbul.anshimgil.data.Usage
import com.deungbul.anshimgil.data.TRIP_CONSENT_NO
import com.deungbul.anshimgil.data.TRIP_CONSENT_YES
import com.deungbul.anshimgil.data.TRIP_CONSENT_QUESTION
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

/** Zoomed out past the walking view (one step out from it already) the map shows no facility tags. */
private const val MIN_TAG_ZOOM = 17

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
    onOpenReport: () -> Unit,
    onPlaceSelected: (Place) -> Unit,
    guardians: List<Guardian>,
    showSos: Boolean,
    modifier: Modifier = Modifier,
    onOpenAssistant: () -> Unit = {},
    /** Open the nearby-bus card on arrival (the assistant asked for it). */
    startWithBuses: Boolean = false,
    onBusesShown: () -> Unit = {},
    startWithSos: Boolean = false,
    onSosShown: () -> Unit = {},
    /** Where the AI 비서 bubble comes from; null shows none. */
    hintSource: MapHintSource? = null,
    /** Open AI 비서 carrying on from a bubble's line. */
    onAskAssistant: (String) -> Unit = {},
    /** AI 비서 asks in its live bubble whether to keep trips; [onTripConsent] gets the answer. */
    askTripConsent: Boolean = false,
    onTripConsent: (Boolean) -> Unit = {},
    /** What has been learned on this phone (쓰는 대로 맞추기). */
    usage: Usage = Usage(),
    /** A change AI 비서 offers now (bigger buttons or text), answered with [onAdaptation]. */
    adaptation: Adaptation? = null,
    onAdaptation: (Adaptation, Boolean) -> Unit = { _, _ -> },
    /**
     * Signals for 쓰는 대로 맞추기: a nearby button used; a press of one of the map's buttons (true when it came
     * right after a tap that missed it, see [MissDetector]); the map zoomed in close by hand.
     */
    onNearbyTap: (String) -> Unit = {},
    onPress: (Boolean) -> Unit = {},
    onZoomIn: () -> Unit = {},
    /** [바로 출발] on a suggestion: guidance at once with the usual kind of route. */
    onStartNow: (Place, Boolean) -> Unit = { _, _ -> },
    /** AI 비서's live words here and the walker's answers, for the chat (fromUser, text). */
    onLive: (Boolean, String) -> Unit = { _, _ -> },
) {
    var sos by remember { mutableStateOf(startWithSos) }
    LaunchedEffect(Unit) { if (startWithSos) onSosShown() }
    val context = LocalContext.current
    var map by remember { mutableStateOf<KakaoMap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var finding by remember { mutableStateOf<String?>(null) }
    var nearbyMessage by remember { mutableStateOf<String?>(null) }
    var bbox by remember { mutableStateOf<Bbox?>(null) }
    var mapNote by remember { mutableStateOf<String?>(null) }
    // The tapped place whose details are shown, e.g. a station's elevator exits.
    var picked by remember { mutableStateOf<Facility?>(null) }
    var showBuses by remember { mutableStateOf(startWithBuses) }
    LaunchedEffect(Unit) { if (startWithBuses) onBusesShown() }
    // What the map shows now, so a station's card can list its exits.
    var shownFacilities by remember { mutableStateOf<List<Facility>>(emptyList()) }
    var zoom by remember { mutableIntStateOf(WALK_ZOOM) }
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf<MapHint?>(null) }
    // 쓰는 대로 맞추기: where each button is on screen, so that a tap just beside one (which does nothing)
    // followed by a tap on it is noticed as a miss; pressing a button again is not.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val buttonBoxes = remember { mutableMapOf<String, ButtonBox>() }
    val missDetector = remember(density) { MissDetector(nearPx = with(density) { MISS_NEAR_DP.dp.toPx() }) }
    var screenOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // Kept in the window's root coordinates; a tap is turned into them with [screenOrigin].
    fun Modifier.tracked(key: String) = onGloballyPositioned { c ->
        val r = c.boundsInRoot()
        buttonBoxes[key] = ButtonBox(r.left, r.top, r.right, r.bottom)
    }

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

    // The AI 비서 bubble: at most one line from real data; gone after a while. Looked for again when the
    // walker has moved about 300 m (a grid cell), since a nearby event depends on where they are.
    val hereCell = here?.let { (it.lat / HINT_CELL_DEG).toInt() to (it.lon / HINT_CELL_DEG).toInt() }
    LaunchedEffect(hintSource, hereCell) {
        val source = hintSource ?: return@LaunchedEffect
        val at = here ?: return@LaunchedEffect
        // A bubble already up stays until it times out or is closed.
        if (hint != null) return@LaunchedEffect
        hint = source.next(mode, at)
    }
    // What AI 비서 says in its bubbles also goes into its chat.
    LaunchedEffect(askTripConsent) { if (askTripConsent) onLive(false, TRIP_CONSENT_QUESTION) }
    LaunchedEffect(adaptation) { adaptation?.let { onLive(false, it.question) } }
    LaunchedEffect(hint) {
        val shown = hint ?: return@LaunchedEffect
        onLive(false, shown.text)
        // A suggested trip stays until answered or closed (it went before it could be tapped, 11/6), or until
        // it is too late for it (past the usual time, the event started).
        if (shown.startNow) {
            val until = shown.until ?: return@LaunchedEffect
            delay(java.time.Duration.between(java.time.LocalDateTime.now(), until).toMillis().coerceAtLeast(0))
            ShownHints.mark(shown.key)
            hint = null
            return@LaunchedEffect
        }
        delay(HINT_SHOW_MS)
        ShownHints.mark(shown.key)
        hint = null
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
        m.setOnCameraMoveEndListener { moved, position, gesture ->
            // Zooming in close (past the walking view) is a sign the map is hard to read.
            // Only the walker's own zooming counts, not the app moving the map (내 위치, a found place).
            if (gesture in HAND_ZOOMS && position.zoomLevel > zoom && position.zoomLevel >= WALK_ZOOM + 2) onZoomIn()
            zoom = position.zoomLevel
            moved.visibleBbox()?.let { bbox = it }
        }
        m.setOnLabelClickListener { _, _, label ->
            // That tap opened something: not a miss even if it was beside a button.
            missDetector.forget()
            (label.tag as? Facility)?.let { picked = it; showBuses = false }
            true
        }
    }
    LaunchedEffect(map, mode, extraMapItems, bbox, zoom) {
        val m = map ?: return@LaunchedEffect
        val area = bbox ?: return@LaunchedEffect
        delay(300) // wait for the map to settle after a drag
        // Zoomed far out the user is looking for a place, not at what is on each street: no tags then.
        if (zoom < MIN_TAG_ZOOM) {
            m.showLayers(context, MapLayers(emptyList(), emptyList()))
            shownFacilities = emptyList()
            me = here?.let { m.addMyLocation(context, it) }
            mapNote = "확대하면 시설이 보여요"
            return@LaunchedEffect
        }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { screenOrigin = it.positionInRoot() }
            // Watches every tap on the screen without taking it (the map and the buttons still get it).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var tap = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        // Two fingers (pinching) are no tap.
                        if (event.changes.size > 1) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        if (!change.pressed) {
                            tap = change.uptimeMillis - down.uptimeMillis <= TAP_MAX_MS
                            break
                        }
                    }
                    if (tap) {
                        missDetector.tap(down.position.x + screenOrigin.x, down.position.y + screenOrigin.y, down.uptimeMillis, buttonBoxes.toMap())
                            ?.let { (_, missed) -> onPress(missed) }
                    }
                }
            },
    ) {
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
                        .heightIn(min = 52.dp)
                        .tracked("search"),
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
                    modifier = Modifier.size(52.dp).tracked("settings"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Tune, contentDescription = "설정", tint = Ink)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Most used first (쓰는 대로 맞추기).
                nearbyOrder(usage).mapNotNull { k -> NEARBY.firstOrNull { it.first == k } }.forEach { (query, label, icon) ->
                    NearbyButton(
                        text = if (finding == query) "찾는 중…" else label,
                        icon = icon,
                        onClick = { onNearbyTap(query); goToNearest(query, label) },
                        modifier = Modifier.weight(1f).tracked(query),
                    )
                }
            }
            // 불편 신고: a small icon on the right, under the nearby buttons (team, 11/6).
            Surface(
                onClick = onOpenReport,
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.End).size(56.dp).tracked("report"),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Campaign, contentDescription = "불편 신고", tint = Color(0xFFB45309), modifier = Modifier.size(24.dp))
                    Text("신고", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
            }
            nearbyMessage?.let {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 2.dp) {
                    Text(it, fontSize = 14.sp, color = Color(0xFFB42318), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }

        // The mode as a tiny icon in the gap under the status bar, left side (map only).
        ModeBadge(mode, Modifier.align(Alignment.TopStart).padding(start = 20.dp))

        // The phone's position when it is known and in Gwangju, otherwise the demo area.
        Surface(
            onClick = { if (hasPermission) locate() else permissionLauncher.launch(LOCATION_PERMISSIONS) },
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 48.dp)
                .size(52.dp)
                .tracked("locate"),
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

        if (showBuses) {
            NearbyBusCard(
                at = here?.let { chooseOrigin(it).point },
                mode = mode,
                onClose = { showBuses = false },
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 112.dp).tracked("_buses"),
            )
        }
        picked?.let { place ->
            PlaceDetailCard(
                place,
                exits = if (place.kind == FacilityKind.SUBWAY) stationExits(place, shownFacilities) else emptyList(),
                onClose = { picked = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 112.dp).tracked("_place"),
            )
        }

        error?.let { MapMessage(it, Modifier.align(Alignment.BottomCenter)) }

        // AI 비서's one-line bubble sits just above its button, unless a card is open there.
        // "자주 가는 길을 기억해 둘까요?" comes first, in the same live bubble, with the two answers under it.
        if (askTripConsent && !showBuses && picked == null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 132.dp).tracked("_ask")) {
                AiAskBubble(
                    TRIP_CONSENT_QUESTION,
                    answers = listOf(
                        TRIP_CONSENT_YES to { onLive(true, TRIP_CONSENT_YES); onTripConsent(true) },
                        TRIP_CONSENT_NO to { onLive(true, TRIP_CONSENT_NO); onTripConsent(false) },
                    ),
                )
            }
        } else if (adaptation != null && !showBuses && picked == null) {
            // 쓰는 대로 맞추기: asked before anything changes.
            Box(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 132.dp).tracked("_ask")) {
                AiAskBubble(
                    adaptation.question,
                    answers = listOf(
                        adaptation.yes to { onLive(true, adaptation.yes); onAdaptation(adaptation, true) },
                        "괜찮아요" to { onLive(true, "괜찮아요"); onAdaptation(adaptation, false) },
                    ),
                )
            }
        } else if (!showBuses && picked == null) hint?.let { h ->
            Box(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 148.dp).tracked("_hint")) {
                HintBubble(
                    h,
                    onOpen = {
                        ShownHints.mark(h.key)
                        hint = null
                        val place = h.place
                        if (place != null) { onLive(true, "길 보기"); onPlaceSelected(place) } else onAskAssistant(h.text)
                    },
                    onDismiss = { ShownHints.mark(h.key); hint = null },
                    onStartNow = h.place?.takeIf { h.startNow }?.let { place ->
                        {
                            onLive(true, "바로 출발")
                            ShownHints.mark(h.key)
                            hint = null
                            onStartNow(place, h.transit)
                        }
                    },
                )
            }
        }
        // AI 비서: a rounded square at the bottom centre; its own screen has the microphone (11/6).
        Surface(
            onClick = onOpenAssistant,
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1F6B42),
            contentColor = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).heightIn(min = 56.dp).tracked("ai"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(" AI 비서", fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        if (showSos) SosButton(onClick = { sos = true }, modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 44.dp))
        if (sos) SosOverlay(guardians, here = { here }, onClose = { sos = false }, onAddGuardian = onOpenSettings)
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

    // A station's next trains each way, from the server's timetable (live position when it knows it).
    var trains by remember(place) { mutableStateOf<List<String>?>(null) }
    var trainsFailed by remember(place) { mutableStateOf(false) }
    if (place.kind == FacilityKind.SUBWAY) {
        LaunchedEffect(place) {
            trains = try {
                matchStation(place.name, SubwayTrains.near(place.lat, place.lon))?.let { nextTrainLines(it.trains) } ?: emptyList()
            } catch (e: ApiException) {
                trainsFailed = true
                null
            }
        }
    }

    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The kind ("지하철역") sits small above the name so a long station name keeps one line.
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    kindText?.let { Text(it, fontSize = 13.sp, color = Color(0xFF5E6A62)) }
                    Text(place.name ?: place.label, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Ink) }
            }
            if (place.kind == FacilityKind.SUBWAY) {
                val next = trains
                when {
                    next == null && !trainsFailed -> Text("다음 열차 찾는 중…", fontSize = 15.sp, color = Color(0xFF5E6A62))
                    next.isNullOrEmpty() -> Text("다음 열차 정보가 없어요", fontSize = 15.sp, color = Color(0xFF5E6A62))
                    else -> Column(
                        Modifier
                            .padding(end = 12.dp, bottom = 6.dp)
                            .fillMaxWidth()
                            .background(Color(0xFFEFF4FB), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("다음 열차", fontSize = 13.sp, color = Color(0xFF2F5BB7), fontWeight = FontWeight.Bold)
                        next.forEach { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink) }
                    }
                }
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

/** The nearest bus stops with the buses coming to each (soonest first; low-floor first for seniors and wheelchair users). */
@Composable
private fun NearbyBusCard(at: LatLon?, mode: UserMode, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var stops by remember { mutableStateOf<List<NearbyStop>?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(at) {
        val from = at ?: phoneOrigin(context).point
        try {
            stops = BusRepository.nearby(from).filter { it.buses.isNotEmpty() }.take(4)
        } catch (e: ApiException) {
            failed = e.message
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("가까운 정류장", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f).padding(top = 8.dp))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Ink) }
            }
            val list = stops
            when {
                failed != null -> Text(failed!!, fontSize = 15.sp, color = Color(0xFFB42318))
                list == null -> Text("곧 오는 버스를 찾는 중…", fontSize = 15.sp, color = Color(0xFF5E6A62))
                list.isEmpty() -> Text("가까운 정류장에 곧 오는 버스가 없어요", fontSize = 15.sp, color = Color(0xFF5E6A62))
                else -> list.forEach { stop ->
                    Column(Modifier.padding(top = 8.dp, end = 12.dp)) {
                        Text(
                            "${stop.name} · ${stop.distanceM}m" + (stop.towards?.let { "  ($it 방면)" } ?: ""),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink,
                        )
                        busesToShow(stop.buses, mode).take(4).forEach { b ->
                            val whenText = when (val m = b.inMin) { null -> "시간 모름"; 0 -> "곧 도착"; else -> "${m}분 뒤" }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${b.line}번", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2F5BB7))
                                Text(whenText + (b.stopsAway?.takeIf { it > 0 }?.let { " (${it}정거장 전)" } ?: ""), fontSize = 15.sp, color = Ink)
                                if (b.lowFloor) {
                                    Text(
                                        "저상", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                        modifier = Modifier.background(Color(0xFF1F6B42), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/** About 300 m; moving into another cell of this size looks for a new bubble. */
private const val HINT_CELL_DEG = 0.003

/** Buttons this much bigger when the walker said yes to bigger buttons. */

/** The walker's own zooming in on the map (not the app moving it). */
private val HAND_ZOOMS = setOf(
    com.kakao.vectormap.GestureType.Zoom,
    com.kakao.vectormap.GestureType.OneFingerZoom,
    com.kakao.vectormap.GestureType.OneFingerDoubleTap,
    com.kakao.vectormap.GestureType.RotateZoom,
)

/** How long the AI 비서 bubble stays when left alone. */
private const val HINT_SHOW_MS = 15_000L

/** One line from AI 비서 under its button; a route offer has its own button, other lines open AI 비서. */
@Composable
private fun HintBubble(hint: MapHint, onOpen: () -> Unit, onDismiss: () -> Unit, onStartNow: (() -> Unit)? = null) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        // See-through so the map behind stays visible; the words keep full contrast.
        color = Color.White.copy(alpha = AI_BUBBLE_ALPHA),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1F6B42).copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "AI 비서", tint = Color(0xFF1F6B42), modifier = Modifier.size(18.dp))
            Text(
                hint.text, fontSize = 15.sp, color = Ink, lineHeight = 21.sp, maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (hint.place != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // One tap to go: guidance starts with the kind of route the walker usually picks.
                    onStartNow?.let { start ->
                        Surface(onClick = start, shape = RoundedCornerShape(14.dp), color = Color(0xFF1F6B42), contentColor = Color.White) {
                            Text("바로 출발", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = onOpen) {
                        Text("길 보기", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F6B42))
                    }
                }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "닫기", tint = Color(0xFF5E6A62)) }
        }
    }
}


/** AI 비서 asking in its live bubble, with ready answers to tap right under the words. */
@Composable
private fun AiAskBubble(text: String, answers: List<Pair<String, () -> Unit>>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = AI_BUBBLE_ALPHA),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1F6B42)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "AI 비서", tint = Color(0xFF1F6B42), modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Text(text, fontSize = 15.sp, color = Ink, lineHeight = 21.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 26.dp)) {
                answers.forEachIndexed { i, (label, onClick) ->
                    Surface(
                        onClick = onClick,
                        shape = RoundedCornerShape(18.dp),
                        color = if (i == 0) Color(0xFF1F6B42) else Color.White,
                        contentColor = if (i == 0) Color.White else Color(0xFF1F6B42),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F6B42)),
                    ) {
                        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}
