package com.deungbul.anshimgil

import androidx.compose.runtime.DisposableEffect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.deungbul.anshimgil.ui.guide.GuideScreen
import com.deungbul.anshimgil.ui.route.RouteScreen
import com.deungbul.anshimgil.ui.settings.SettingsScreen
import com.deungbul.anshimgil.ui.report.ReportScreen
import com.deungbul.anshimgil.ui.search.SearchScreen
import com.deungbul.anshimgil.ui.common.MapMessage
import com.deungbul.anshimgil.ui.home.HomeScreen
import com.deungbul.anshimgil.ui.mode.ModeSelectScreen
import com.deungbul.anshimgil.ui.mode.SetupChatScreen
import androidx.compose.material.icons.filled.AutoAwesome
import com.deungbul.anshimgil.location.lastKnownLocation
import androidx.compose.ui.unit.dp
import com.deungbul.anshimgil.ui.assistant.AssistantChat
import com.deungbul.anshimgil.ui.guide.TransitGuideScreen
import com.deungbul.anshimgil.location.currentLocation
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.repeatOnLifecycle
import com.deungbul.anshimgil.ui.assistant.AssistantScreen
import com.deungbul.anshimgil.ui.theme.SeipjidoTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.deungbul.anshimgil.data.assistant.AppAssistant
import com.deungbul.anshimgil.data.assistant.AssistantAction
import com.deungbul.anshimgil.data.assistant.AssistantContext
import com.deungbul.anshimgil.data.assistant.ChatStore
import com.deungbul.anshimgil.data.assistant.favoriteIn
import com.deungbul.anshimgil.data.guide.GuideStore
import com.deungbul.anshimgil.data.guide.SavedGuide
import com.deungbul.anshimgil.data.guide.WalkSpeedRepository
import com.deungbul.anshimgil.data.guide.personalSpeed
import com.deungbul.anshimgil.data.place.EventRepository
import com.deungbul.anshimgil.data.place.FacilityRepository
import com.deungbul.anshimgil.data.place.Place
import com.deungbul.anshimgil.data.place.PlaceRepository
import com.deungbul.anshimgil.data.place.SavedPlacesRepository
import com.deungbul.anshimgil.data.route.LatLon
import com.deungbul.anshimgil.data.route.RouteRepository
import com.deungbul.anshimgil.data.route.RouteRequest
import com.deungbul.anshimgil.data.route.RouteResult
import com.deungbul.anshimgil.data.route.RouteType
import com.deungbul.anshimgil.data.route.StopPlace
import com.deungbul.anshimgil.data.route.TransitRoute
import com.deungbul.anshimgil.data.route.night
import com.deungbul.anshimgil.data.route.withStop
import com.deungbul.anshimgil.data.safety.FeedbackStep
import com.deungbul.anshimgil.data.safety.TripConsent
import com.deungbul.anshimgil.data.safety.TripLogRepository
import com.deungbul.anshimgil.data.safety.feedbackFor
import com.deungbul.anshimgil.data.safety.nextFeedbackStep
import com.deungbul.anshimgil.data.safety.tripWeather
import com.deungbul.anshimgil.data.server.AppUpdateCheck
import com.deungbul.anshimgil.data.server.ServerApi
import com.deungbul.anshimgil.data.user.Adaptation
import com.deungbul.anshimgil.data.user.AppOpenLog
import com.deungbul.anshimgil.data.user.AppSettings
import com.deungbul.anshimgil.data.user.CONSENT_NO_REPLY
import com.deungbul.anshimgil.data.user.MapHintSource
import com.deungbul.anshimgil.data.user.ModeRepository
import com.deungbul.anshimgil.data.user.MyInfo
import com.deungbul.anshimgil.data.user.MyInfoRepository
import com.deungbul.anshimgil.data.user.SETUP_UNDO_INDEX
import com.deungbul.anshimgil.data.user.SettingsRepository
import com.deungbul.anshimgil.data.user.SetupAnswers
import com.deungbul.anshimgil.data.user.SetupAssistant
import com.deungbul.anshimgil.data.user.SetupStep
import com.deungbul.anshimgil.data.user.ShownHints
import com.deungbul.anshimgil.data.user.TempModeStore
import com.deungbul.anshimgil.data.user.TempState
import com.deungbul.anshimgil.data.user.TextSize
import com.deungbul.anshimgil.data.user.Usage
import com.deungbul.anshimgil.data.user.UsageRepository
import com.deungbul.anshimgil.data.user.UserMode
import com.deungbul.anshimgil.data.user.YES_NO
import com.deungbul.anshimgil.data.user.adaptationToOffer
import com.deungbul.anshimgil.data.user.aidProfileValue
import com.deungbul.anshimgil.data.user.answerSetup
import com.deungbul.anshimgil.data.user.calendarHint
import com.deungbul.anshimgil.data.user.habits
import com.deungbul.anshimgil.data.user.hasCalendarPermission
import com.deungbul.anshimgil.data.user.preferredRoute
import com.deungbul.anshimgil.data.user.setupAnswerFromSpeech
import com.deungbul.anshimgil.data.user.setupQuestion
import com.deungbul.anshimgil.data.user.tempEndQuestion
import com.deungbul.anshimgil.data.route.eunNeun

/** What was set before a change, to take it back ([mode] is null before one is picked). */
data class SettingsSnapshot(val mode: UserMode?, val settings: AppSettings, val usage: Usage)

class MainActivity : ComponentActivity() {
    /** A place to open from the 출발 시간 알림 (name, point, by transit), handled once by AppRoot. */
    private val reminded = androidx.compose.runtime.mutableStateOf<Triple<Place, Boolean, Boolean>?>(null)

    private fun readReminder(intent: android.content.Intent?) {
        val name = intent?.getStringExtra(EXTRA_GO_NAME) ?: return
        val lat = intent.getDoubleExtra(EXTRA_GO_LAT, Double.NaN)
        val lon = intent.getDoubleExtra(EXTRA_GO_LON, Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return
        reminded.value = Triple(
            Place(null, name, "", lat, lon, null),
            intent.getBooleanExtra(EXTRA_GO_TRANSIT, false),
            // 길 안내 예약: at the booked time the guidance starts by itself.
            intent.getBooleanExtra(EXTRA_GO_START, false),
        )
        intent.removeExtra(EXTRA_GO_START)
        intent.removeExtra(EXTRA_GO_NAME)
    }

    /** In the small window (PIP) now. */
    private val inPip = androidx.compose.runtime.mutableStateOf(false)
    /** Walking guidance is on screen: leaving the app shrinks it to the small window instead of hiding it (11/13). */
    private var guidingNow = false

    private fun canPip() = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(): android.app.PictureInPictureParams =
        android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(3, 4))
            .apply { if (android.os.Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(guidingNow) }
            .build()

    private fun setGuiding(on: Boolean) {
        guidingNow = on
        if (canPip()) runCatching { setPictureInPictureParams(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ shrinks by itself (setAutoEnterEnabled); before that, on the home button here.
        if (guidingNow && canPip() && android.os.Build.VERSION.SDK_INT < 31) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readReminder(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readReminder(intent)
        // 출발 시간 알림: a check every 15 minutes (it does nothing without the trip-record yes).
        runCatching { DepartureWorker.schedule(applicationContext) }
        ShownHints.init(applicationContext)
        enableEdgeToEdge()
        val modeRepository = ModeRepository(applicationContext)
        val savedPlaces = SavedPlacesRepository(applicationContext)
        val placeRepository = PlaceRepository.create()
        val facilityRepository = FacilityRepository.create()
        val settingsRepository = SettingsRepository(applicationContext)
        val walkSpeedRepository = WalkSpeedRepository(applicationContext)
        ServerApi.guest = { walkSpeedRepository.guestId() to walkSpeedRepository.guestToken() }
        val routeRepository = RouteRepository.create(guestId = { walkSpeedRepository.guestId() })
        val tripLog = TripLogRepository(applicationContext, guestId = { walkSpeedRepository.guestId() })
        val myInfoRepository = com.deungbul.anshimgil.data.user.MyInfoRepository(guestId = { walkSpeedRepository.guestId() })
        val usageRepository = UsageRepository(applicationContext)
        // Walks that could not be sent last time (offline, server not ready) go now.
        lifecycleScope.launch { runCatching { walkSpeedRepository.uploadPending() } }
        // 업데이트 안내: asked once a run; a failure or odd answer shows nothing.
        lifecycleScope.launch { com.deungbul.anshimgil.data.server.AppUpdateCheck.checkOnce() }
        // The mode next to the clock while the app is in front; taken away in onStop.
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                modeRepository.mode.collect { mode -> if (mode != null) ModeStatus.show(this@MainActivity, mode) else ModeStatus.hide(this@MainActivity) }
            }
        }
        setContent {
            // Asked once each, after a mode is chosen: notifications (Android 13+) for the mode icon next to the clock,
            // and the position, so a new walker's map, nearby places and routes start from where they are rather than
            // from Chosun University until they find the location button (11/10 phone check). One request, so the two
            // dialogs come one after the other instead of the second being dropped.
            val firstPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
                if (granted[android.Manifest.permission.POST_NOTIFICATIONS] == true) {
                    lifecycleScope.launch { modeRepository.mode.first()?.let { ModeStatus.show(this@MainActivity, it) } }
                }
            }
            val hasMode by modeRepository.mode.map { it != null }.collectAsStateWithLifecycle(initialValue = false)
            LaunchedEffect(hasMode) {
                if (!hasMode) return@LaunchedEffect
                val prefs = getSharedPreferences("app", MODE_PRIVATE)
                val ask = buildList {
                    if (android.os.Build.VERSION.SDK_INT >= 33 && !ModeStatus.canShow(this@MainActivity) && !prefs.getBoolean("askedNotify", false)) {
                        add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!com.deungbul.anshimgil.location.hasLocationPermission(this@MainActivity) && !prefs.getBoolean("askedLocation", false)) {
                        addAll(com.deungbul.anshimgil.location.LOCATION_PERMISSIONS)
                    }
                }
                if (ask.isEmpty()) return@LaunchedEffect
                prefs.edit().putBoolean("askedNotify", true).putBoolean("askedLocation", true).apply()
                firstPermissions.launch(ask.toTypedArray())
            }
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            // AI 비서's text enlargement, on top of the text size in settings (쓰는 대로 맞추기).
            val boost by usageRepository.usage.map { if (it.adapt) it.textBoost else 1f }.collectAsStateWithLifecycle(initialValue = 1f)
            SeipjidoTheme {
                ScaledText(settings.textSize, boost) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        AppRoot(
                            modeRepository, savedPlaces, placeRepository, facilityRepository, routeRepository,
                            settingsRepository, settings, walkSpeedRepository, Modifier.padding(innerPadding),
                            tripLog = tripLog,
                            myInfoRepository = myInfoRepository,
                            usageRepository = usageRepository,
                            reminded = reminded.value,
                            onRemindedHandled = { reminded.value = null },
                            inPip = inPip.value,
                            onGuiding = ::setGuiding,
                        )
                    }
                    val update by com.deungbul.anshimgil.data.server.AppUpdateCheck.update.collectAsStateWithLifecycle()
                    update?.let { u ->
                        com.deungbul.anshimgil.ui.common.AppUpdateDialog(
                            u,
                            onUpdate = { openDownload(u.downloadUrl) },
                            onLater = com.deungbul.anshimgil.data.server.AppUpdateCheck::later,
                        )
                    }
                }
            }
        }
    }

    /** [업데이트]: the download address in the phone's browser. */
    private fun openDownload(url: String) {
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "인터넷 창을 열 수 없어요. 잠시 뒤 다시 해 주세요.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        appOnScreen = true
    }

    override fun onStop() {
        super.onStop()
        appOnScreen = false
        ModeStatus.hide(this)
    }
}

/** Applies the text size chosen in settings on top of the phone's own font size. */
@Composable
private fun ScaledText(size: TextSize, boost: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * size.scale * boost),
        content = content,
    )
}

/** Saved mode as read from the phone; [Loading] until the first read finishes. */
private sealed interface SavedMode {
    data object Loading : SavedMode
    data class Ready(val mode: UserMode?) : SavedMode
}

/** Screens reachable from the home map. */
private sealed interface Screen {
    data object Home : Screen
    /** [startFor] is set while choosing a new start for that route. */
    /** [stopFor] is set while choosing a place to go via (경유지) on that route. */
    /** [destFor] is set while choosing another destination for that route. */
    data class Search(val startFor: Route? = null, val stopFor: Route? = null, val destFor: Route? = null) : Screen
    data object Settings : Screen
    /** 내 정보 (m06): stairs, going round, resting, transit, keeping trips. */
    data object MyInfo : Screen
    /** 불편 신고 (FR-49). */
    data object Report : Screen

    /** 말로 하기 on the map screen. */
    data object Assistant : Screen
    /** [start] null means the phone's position. [fromHome]: opened from a home shortcut, so back returns home. */
    /** [transit] opens on the 대중교통 tab (AI 비서 recommended the bus or subway). */
    /** [autoStart]: guidance starts as soon as the route is found ([바로 출발]). */
    data class Route(
        val destination: Place,
        val start: Place? = null,
        val fromHome: Boolean = false,
        val transit: Boolean = false,
        val autoStart: Boolean = false,
        /** 들를 곳 on the way, in order (AI 비서 planned them). */
        val stops: List<com.deungbul.anshimgil.data.route.StopPlace> = emptyList(),
    ) : Screen
    /** [resumed]: taken up again after Android closed the app during it (이어서 안내). */
    data class Guide(val route: Route, val result: RouteResult, val type: RouteType, val arriveAt: String? = null, val resumed: Boolean = false) : Screen
    data class TransitGuide(val route: Route, val transit: TransitRoute) : Screen
}

/** First launch shows the mode picker; after that the map opens directly. */
@Composable
fun AppRoot(
    modeRepository: ModeRepository,
    savedPlaces: SavedPlacesRepository,
    placeRepository: PlaceRepository,
    facilityRepository: FacilityRepository,
    routeRepository: RouteRepository,
    settingsRepository: SettingsRepository,
    settings: AppSettings,
    walkSpeedRepository: WalkSpeedRepository,
    modifier: Modifier = Modifier,
    tripLog: TripLogRepository? = null,
    myInfoRepository: com.deungbul.anshimgil.data.user.MyInfoRepository = com.deungbul.anshimgil.data.user.MyInfoRepository(guestId = { walkSpeedRepository.guestId() }),
    usageRepository: UsageRepository? = null,
    /** From the 출발 시간 알림 or a booked guidance: open the route there (place, by transit, start at once). */
    reminded: Triple<Place, Boolean, Boolean>? = null,
    onRemindedHandled: () -> Unit = {},
    /** In the small window (PIP) over other apps: walking guidance shows only the map and the next turn. */
    inPip: Boolean = false,
    /** Walking guidance is open (true) or closed: the home button then shrinks it to the small window. */
    onGuiding: (Boolean) -> Unit = {},
) {
    val savedFlow = remember(modeRepository) { modeRepository.mode.map { SavedMode.Ready(it) } }
    val saved by savedFlow.collectAsStateWithLifecycle(initialValue = SavedMode.Loading)
    var changingMode by rememberSaveable { mutableStateOf(false) }
    // 말로 맞추기 open on top of the mode picker.
    var talking by rememberSaveable { mutableStateOf(false) }
    // 처음 맞추기: AI 비서's few questions on the map after the first mode pick ([SetupStep]).
    var setupStep by rememberSaveable { mutableStateOf<SetupStep?>(null) }
    var setupAnswers by remember { mutableStateOf(SetupAnswers()) }
    // 사용성 점검표 21번: what was set before a change, so a mistaken tap or answer can be taken back at once.
    var setupBefore by remember { mutableStateOf<SettingsSnapshot?>(null) }
    var assistantUndo by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val savedFavorites by savedPlaces.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    var homeUndo by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    // 몸 상태가 잠깐 바뀜 (TempMode.kt): the end-day question, and telling the server (PATCH /api/me temp fields).
    var tempAsk by remember { mutableStateOf<String?>(null) }
    // The server's prefer_transit (0 walking, 1 either, 2 transit), read when the app opens.
    var serverPreferTransit by remember { mutableStateOf<Int?>(null) }
    val tempScope = rememberCoroutineScope()
    fun tellServerTemp(st: com.deungbul.anshimgil.data.user.TempState?, usual: UserMode? = st?.usual) {
        val u = usual ?: return
        if (!com.deungbul.anshimgil.data.server.ServerApi.isConfigured) return
        tempScope.launch {
            runCatching {
                val f = if (st == null) org.json.JSONObject().put("clearTemp", true)
                else org.json.JSONObject().put("tempMode", st.temp.key).put("tempUntil", st.until.toString()).put("tempReason", st.reason)
                myInfoRepository.update(u, f)
            }
        }
    }
    suspend fun endTempMode(st: com.deungbul.anshimgil.data.user.TempState) {
        modeRepository.save(st.usual)
        if (st.temp == UserMode.DISABLED) listOf("toilet:wheelchair", "crosswalk:curb_low").forEach { settingsRepository.setExtraMapItem(it, false) }
        com.deungbul.anshimgil.data.user.TempModeStore.clear(appContext)
        tellServerTemp(null, st.usual)
    }
    val setupAssistant = remember { SetupAssistant.create() }
    val appAssistant = remember {
        AppAssistant.create(
            guestId = { walkSpeedRepository.guestId() },
            favorites = { savedPlaces.favorites.first() },
        )
    }
    // The assistant asked the map to open the nearby-bus card.
    var busesOnHome by remember { mutableStateOf(false) }
    // 불편 신고 filled in by AI 비서 ("여기 공사 중이라 못 지나가").
    var reportPrefill by remember { mutableStateOf<AssistantAction.OpenReport?>(null) }
    // AI 비서 opened from its bubble on the map starts with that line.
    var assistantOpening by remember { mutableStateOf<String?>(null) }
    // Opened from the map's microphone button, so it listens at once.
    var assistantListen by remember { mutableStateOf(false) }
    // AI 비서's conversation: kept on the phone, also after the app is closed, until the walker clears it (11/12).
    val assistantChat = remember { AssistantChat(com.deungbul.anshimgil.data.assistant.ChatStore.load(appContext)) }
    LaunchedEffect(assistantChat) {
        androidx.compose.runtime.snapshotFlow { assistantChat.lines.toList() }
            .collect { com.deungbul.anshimgil.data.assistant.ChatStore.save(appContext, it) }
    }
    val mapHints = remember {
        MapHintSource(
            EventRepository.create(),
            guestId = { walkSpeedRepository.guestId() },
            openLog = AppOpenLog(appContext, keep = { tripLog?.consent?.first() == TripConsent.YES }),
            usage = { usageRepository?.current() },
            home = { savedPlaces.favorites.first().firstOrNull { it.name == "집" }?.place },
            calendar = { here, now -> calendarHint(appContext, placeRepository, here, now) },
        )
    }
    // The assistant's 보호자에게 알리기: open SOS on the map.
    var sosOnHome by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()
    // 캘린더 일정으로 길 알려 주기: on only with the permission; off at once.
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { usageRepository?.setCalendar(granted) }
    }
    fun useCalendar(on: Boolean) {
        if (on && !hasCalendarPermission(appContext)) calendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
        else scope.launch { usageRepository?.setCalendar(on) }
    }
    // 이동 기록: asked once, after the first finished guidance.
    val tripConsent by (tripLog?.consent ?: kotlinx.coroutines.flow.flowOf(TripConsent.NO)).collectAsStateWithLifecycle(initialValue = TripConsent.NO)
    var askTripConsent by remember { mutableStateOf(false) }
    // The server's record for the guidance now running, and when it started.
    var tripId by remember { mutableStateOf<Long?>(null) }
    var tripDestination by remember { mutableStateOf<Place?>(null) }
    LaunchedEffect(reminded) {
        val (place, transit, start) = reminded ?: return@LaunchedEffect
        screen = Screen.Route(place, fromHome = true, transit = transit, autoStart = start)
        onRemindedHandled()
    }
    // 쓰는 대로 맞추기: what has been learned, and a change to offer (after the trip question, one at a time).
    val usage by (usageRepository?.usage ?: kotlinx.coroutines.flow.flowOf(Usage())).collectAsStateWithLifecycle(initialValue = Usage())
    var guideArrived by remember { mutableStateOf(false) }
    var guideStartedAt by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    /** At the start of a guidance: start the server's record (when the walker said yes). */
    fun startTrip(destination: Place, transit: Boolean, result: RouteResult?, type: RouteType?, mode: UserMode) {
        guideStartedAt = java.time.LocalDateTime.now()
        tripId = null
        tripDestination = destination
        // Only with a yes to keeping trips (팀 결정 11-09): nothing about places is kept on the phone otherwise.
        if (tripConsent != TripConsent.YES) return
        // Leaving the place last arrived at: how long the walker stays there is learned (for "집으로 갈까요?").
        // Also kept for the habits (이동 패턴): where to and when.
        scope.launch { runCatching { usageRepository?.leaving(lastKnownLocation(appContext), destination, transit, guideStartedAt) } }
        val log = tripLog ?: return
        val route = result?.let { r -> type?.let { r.route(it) } ?: r.routes.firstOrNull() }
        scope.launch {
            tripId = runCatching {
                log.start(
                    destination, mode, transit,
                    routeKind = if (transit) null else route?.type?.name?.lowercase(),
                    weather = tripWeather(result),
                    night = result?.conditions?.any { it.key == "night" && it.on } == true,
                    plannedMin = route?.durationMin,
                )
            }.getOrNull()
        }
    }
    // 도착 뒤 한마디 (m07): the trip to send it with, the question now, and what the server learned from it.
    var feedbackTrip by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var feedbackStep by remember { mutableStateOf<FeedbackStep?>(null) }
    var learnedSay by remember { mutableStateOf<String?>(null) }
    /** At the end: close the record (arrived or stopped), or ask the first time. */
    fun finishTrip(arrived: Boolean) {
        if (arrived && tripConsent == TripConsent.YES) tripDestination?.let { place ->
            scope.launch { runCatching { usageRepository?.arrived(place, java.time.LocalDateTime.now()) } }
        }
        val log = tripLog ?: return
        when (tripConsent) {
            TripConsent.UNASKED -> askTripConsent = true
            TripConsent.YES -> tripId?.let { id ->
                val minutes = java.time.Duration.between(guideStartedAt, java.time.LocalDateTime.now()).toMinutes().toInt()
                scope.launch { runCatching { log.end(id, arrived, minutes) } }
                // Arrived: ask how the way was, to send with the trip.
                if (arrived) { feedbackTrip = id to minutes; feedbackStep = FeedbackStep.HOW }
            }
            TripConsent.NO -> Unit
        }
        tripId = null
    }
    // How many trips the server keeps, for settings.
    var tripCount by remember { mutableStateOf<Int?>(null) }
    // Places are learned on the phone only with a yes to keeping trips (팀 결정 11-09): without it (not asked yet, or
    // no) whatever was kept before is forgotten. Read from the saved value, not the screen's starting guess.
    LaunchedEffect(tripLog) {
        tripLog?.consent?.collect { consent ->
            if (consent != TripConsent.YES) runCatching {
                usageRepository?.forgetPlaces()
                AppOpenLog(appContext).clear()
            }
        }
    }
    LaunchedEffect(screen, tripConsent) {
        if (screen == Screen.Settings && tripConsent == TripConsent.YES) tripCount = runCatching { tripLog?.count() }.getOrNull()
    }

    when (val state = saved) {
        SavedMode.Loading -> Box(modifier.fillMaxSize())
        is SavedMode.Ready -> {
            val mode = state.mode
            // The server keeps this phone's guest number and mode (진행표 10); quietly, and again next time if offline.
            LaunchedEffect(mode) {
                val m = mode ?: return@LaunchedEffect
                // During a temporary change the server keeps the usual mode and the temporary one apart (temp_mode).
                val usual = com.deungbul.anshimgil.data.user.TempModeStore.get(appContext)?.usual ?: m
                if (com.deungbul.anshimgil.data.server.ServerApi.isConfigured) runCatching { myInfoRepository.syncMode(usual) }
                // Walking or transit first: the server learns it from the trip records (PreferenceLearner).
                if (com.deungbul.anshimgil.data.server.ServerApi.isConfigured) runCatching { serverPreferTransit = myInfoRepository.load().preferTransit }
            }
            // 이어서 안내 (11/13): guidance left when Android closed the app goes on with the same route.
            LaunchedEffect(mode) {
                val m = mode ?: return@LaunchedEffect
                if (screen != Screen.Home) return@LaunchedEffect
                val g = GuideStore.load(appContext) ?: return@LaunchedEffect
                val result = runCatching { routeRepository.findRoutes(RouteRequest(g.from, g.to, m, stops = g.stops)) }.getOrNull()
                if (result == null || result.routes.isEmpty() || screen != Screen.Home) return@LaunchedEffect
                val type = if (result.route(g.type) != null) g.type else result.routes.first().type
                screen = Screen.Guide(Screen.Route(g.destination, start = g.start, fromHome = true, stops = g.stops), result, type, g.arriveAt, resumed = true)
            }
            // 몸 상태가 잠깐 바뀜: past the end day the usual way of walking is back; on the end day, ask (team server rule).
            LaunchedEffect(Unit) {
                val st = com.deungbul.anshimgil.data.user.TempModeStore.get(appContext) ?: return@LaunchedEffect
                val today = java.time.LocalDate.now()
                if (today.isAfter(st.until)) endTempMode(st) else if (today == st.until) tempAsk = com.deungbul.anshimgil.data.user.tempEndQuestion(st)
            }
            // 불편 신고 laid over guidance (it keeps running), and how many were sent during it (진행표: 다시 찾을까요?).
            var reportOverGuide by remember(screen) { mutableStateOf(false) }
            var reportedInGuide by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            val guiding = screen is Screen.Guide || screen is Screen.TransitGuide
            val reportPanel: @Composable (UserMode) -> Unit = { mode ->
                if (reportOverGuide) {
                    BackHandler { reportOverGuide = false; reportPrefill = null }
                    val prefill = reportPrefill
                    ReportScreen(
                        mode = mode,
                        onBack = { reportOverGuide = false; reportPrefill = null },
                        modifier = modifier,
                        initialType = prefill?.type,
                        initialMemo = prefill?.memo.orEmpty(),
                        onSent = { reportOverGuide = false; reportPrefill = null; reportedInGuide++ },
                    )
                }
            }
            // AI 비서, as its own screen from the map or laid over walking guidance (which keeps running underneath).
            val assistantPanel: @Composable (UserMode, () -> Unit) -> Unit = { mode, onClose ->
                    AssistantScreen(
                        opening = assistantOpening,
                        listenAtStart = assistantListen,
                        chat = assistantChat,
                        assistant = appAssistant,
                        context = AssistantContext(mode, settings, usage = usage, where = { currentLocation(appContext, 4_000) }, usualMode = com.deungbul.anshimgil.data.user.TempModeStore.get(appContext)?.usual ?: mode),
                        placeRepository = placeRepository,
                        findSaved = { q -> com.deungbul.anshimgil.data.assistant.favoriteIn(q, savedPlaces.favorites.first())?.let { f -> f.place?.copy(name = f.name) } },
                        onChangeSettings = { change ->
                            val before = SettingsSnapshot(mode, settings, usage)
                            val tempBefore = com.deungbul.anshimgil.data.user.TempModeStore.get(appContext)
                            change.tempUntil?.let { until ->
                                val st = com.deungbul.anshimgil.data.user.TempState(tempBefore?.usual ?: mode, change.mode ?: mode, until, change.tempReason.orEmpty())
                                com.deungbul.anshimgil.data.user.TempModeStore.save(appContext, st)
                                tellServerTemp(st)
                            }
                            if (change.clearTemp) {
                                com.deungbul.anshimgil.data.user.TempModeStore.clear(appContext)
                                tellServerTemp(null, tempBefore?.usual ?: change.mode ?: mode)
                            }
                            assistantUndo = {
                                if (change.tempUntil != null || change.clearTemp) {
                                    if (tempBefore != null) com.deungbul.anshimgil.data.user.TempModeStore.save(appContext, tempBefore)
                                    else com.deungbul.anshimgil.data.user.TempModeStore.clear(appContext)
                                    tellServerTemp(tempBefore, tempBefore?.usual ?: before.mode)
                                }
                                if (change.mode != null) before.mode?.let { modeRepository.save(it) }
                                change.textSize?.let { settingsRepository.setTextSize(before.settings.textSize) }
                                change.voiceGuide?.let { settingsRepository.setVoiceGuide(before.settings.voiceGuide) }
                                change.sosButton?.let { settingsRepository.setSosButton(before.settings.sosButton) }
                                (change.addMapItems + change.removeMapItems).forEach { settingsRepository.setExtraMapItem(it, it in before.settings.extraMapItems) }
                                if (change.undoTextBoost || change.textBoostStepBack || change.textBoostUp) usageRepository?.setTextBoost(before.usage.textBoost)
                                change.adapt?.let { usageRepository?.setAdapt(before.usage.adapt) }
                                change.calendar?.let { useCalendar(!it) }
                            }
                            // On asks for the calendar permission first.
                            change.calendar?.let { useCalendar(it) }
                            scope.launch {
                                change.mode?.let { modeRepository.save(it) }
                                change.textSize?.let { settingsRepository.setTextSize(it) }
                                change.voiceGuide?.let { settingsRepository.setVoiceGuide(it) }
                                change.sosButton?.let { settingsRepository.setSosButton(it) }
                                change.addMapItems.forEach { settingsRepository.setExtraMapItem(it, true) }
                                change.removeMapItems.forEach { settingsRepository.setExtraMapItem(it, false) }
                                if (change.undoTextBoost) usageRepository?.resetTextBoost()
                                if (change.textBoostStepBack) usageRepository?.stepBackTextBoost()
                                if (change.textBoostUp) usageRepository?.stepUpTextBoost()
                                change.adapt?.let { usageRepository?.setAdapt(it) }
                            }
                        },
                        onRoute = { place -> screen = Screen.Route(place, fromHome = true) },
                        onRouteByTransit = { place -> screen = Screen.Route(place, fromHome = true, transit = true) },
                        onRouteWithStops = { place, stops, transit -> screen = Screen.Route(place, fromHome = true, transit = transit, stops = stops) },
                        onRouteTrip = { start, place, stops, transit -> screen = Screen.Route(place, start = start, fromHome = true, transit = transit, stops = stops) },
                        onShowBuses = { busesOnHome = true; screen = Screen.Home },
                        onOpenReport = { r -> reportPrefill = r; if (guiding) { onClose(); reportOverGuide = true } else screen = Screen.Report },
                        onReported = { if (screen is Screen.Guide) { onClose(); reportedInGuide++ } },
                        onAlertGuardian = { sosOnHome = true; screen = Screen.Home },
                        onUndo = {
                            val undo = assistantUndo
                            assistantUndo = null
                            if (undo != null) scope.launch { undo() }
                            undo != null
                        },
                        onBack = onClose,
                        modifier = modifier,
                    )
            }
            // AI 비서 opened over guidance; closed again whenever the screen changes.
            var assistantOverGuide by remember(screen) { mutableStateOf(false) }
            val openAssistantOverGuide = {
                assistantOpening = null
                // Like the map's AI 비서 button: opens without listening at once.
                assistantListen = false
                assistantOverGuide = true
            }
            when {
                (mode == null || changingMode) && talking -> {
                    BackHandler { talking = false }
                    SetupChatScreen(
                        assistant = setupAssistant,
                        onApply = { pick ->
                            scope.launch {
                                modeRepository.save(pick.mode)
                                settingsRepository.setTextSize(pick.textSize)
                                settingsRepository.setVoiceGuide(pick.voiceGuide)
                                settingsRepository.setSosButton(pick.sosButton)
                                pick.extraMapItems.forEach { settingsRepository.setExtraMapItem(it, true) }
                                talking = false
                                changingMode = false
                                screen = Screen.Home
                            }
                        },
                        onBack = { talking = false },
                        modifier = modifier,
                    )
                }
                mode == null || changingMode -> {
                    if (mode != null) BackHandler { changingMode = false }
                    ModeSelectScreen(
                        initial = mode,
                        onStart = { picked ->
                            // The first pick on a new install, or another mode later: AI 비서 offers to set up the rest.
                            // A new walker: the team server's setup questions first (FirstSetup.kt).
                            if (mode == null) setupStep = SetupStep.WAY
                            else if (picked != mode) setupStep = SetupStep.CHANGED
                            setupAnswers = SetupAnswers()
                            val firstPick = mode == null
                            scope.launch {
                                // An older walker starts on the bigger text, body text 18sp and over (사용성 점검표 13번);
                                // "잘 보여요" in 처음 맞추기 keeps it, and 설정 or AI 비서 can make it smaller.
                                if (firstPick && picked == UserMode.SENIOR && settings.textSize == TextSize.NORMAL) settingsRepository.setTextSize(TextSize.LARGE)
                                modeRepository.save(picked)
                                changingMode = false
                                // Straight to the map, also when the mode was changed from settings.
                                screen = Screen.Home
                            }
                        },
                        modifier = modifier,
                        onTalk = { talking = true },
                    )
                }
                BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank() ->
                    MapMessage("local.properties에 KAKAO_NATIVE_APP_KEY를 넣고 다시 빌드해 주세요.", modifier)
                screen == Screen.Assistant -> {
                    BackHandler { screen = Screen.Home }
                    assistantPanel(mode) { screen = Screen.Home }
                }
                screen == Screen.Report -> {
                    BackHandler { screen = Screen.Home }
                    val prefill = reportPrefill
                    ReportScreen(
                        mode = mode,
                        onBack = { reportPrefill = null; screen = Screen.Home },
                        modifier = modifier,
                        initialType = prefill?.type,
                        initialMemo = prefill?.memo.orEmpty(),
                    )
                }
                screen == Screen.MyInfo -> {
                    BackHandler { screen = Screen.Settings }
                    com.deungbul.anshimgil.ui.settings.MyInfoScreen(
                        mode = mode,
                        repository = myInfoRepository,
                        onBack = { screen = Screen.Settings },
                        onChangeMode = { changingMode = true },
                        onConsent = { yes -> tripLog?.setConsent(if (yes) TripConsent.YES else TripConsent.NO, mode) },
                        onDeletedAll = { tripLog?.setConsent(TripConsent.UNASKED, mode) },
                        modifier = modifier,
                    )
                }
                screen == Screen.Settings -> {
                    BackHandler { screen = Screen.Home }
                    SettingsScreen(
                        mode = mode,
                        settings = settings,
                        onBack = { screen = Screen.Home },
                        onChangeMode = { changingMode = true },
                        onManagePlaces = { screen = Screen.Search() },
                        onOpenMyInfo = { screen = Screen.MyInfo },
                        onVoiceChange = { scope.launch { settingsRepository.setVoiceGuide(it) } },
                        onVoiceChatChange = { scope.launch { settingsRepository.setVoiceChat(it) } },
                        onAutoConditionsChange = { scope.launch { settingsRepository.setAutoConditions(it) } },
                        onTextSizeChange = { scope.launch { settingsRepository.setTextSize(it) } },
                        onExtraMapItemChange = { key, on -> scope.launch { settingsRepository.setExtraMapItem(key, on) } },
                        onAddGuardian = { scope.launch { settingsRepository.addGuardian(it) } },
                        onRemoveGuardian = { scope.launch { settingsRepository.removeGuardian(it) } },
                        onTurnSignsChange = { scope.launch { settingsRepository.setTurnSigns(it) } },
                        onSosButtonChange = { scope.launch { settingsRepository.setSosButton(it) } },
                        onResetToDefaults = { scope.launch { settingsRepository.resetToDefaults() } },
                        modifier = modifier,
                        tripConsent = tripConsent == TripConsent.YES,
                        tripCount = tripCount,
                        onTripConsentChange = { on ->
                            scope.launch {
                                tripLog?.setConsent(if (on) TripConsent.YES else TripConsent.NO, mode)
                                tripCount = if (on) runCatching { tripLog?.count() }.getOrNull() else null
                            }
                        },
                        onClearTrips = { (tripLog?.clearAll(mode) ?: true).also { if (it) tripCount = 0 } },
                        adapt = usage.adapt,
                        textBoost = usage.textBoost,
                        onResetTextBoost = { scope.launch { usageRepository?.resetTextBoost() } },
                        onAdaptChange = { on -> scope.launch { usageRepository?.setAdapt(on) } },
                        calendarOn = usage.calendarOn,
                        onCalendarChange = { on -> useCalendar(on) },
                        onResetAdapt = { scope.launch { usageRepository?.reset() } },
                    )
                }
                screen is Screen.Search -> {
                    val startFor = (screen as Screen.Search).startFor
                    val stopFor = (screen as Screen.Search).stopFor
                    val destFor = (screen as Screen.Search).destFor
                    val back = { screen = startFor ?: stopFor ?: destFor ?: Screen.Home }
                    BackHandler(onBack = back)
                    // key: a fresh search box when switching between destination, start and a place on the way.
                    androidx.compose.runtime.key(startFor, stopFor, destFor) {
                        SearchScreen(
                            placeRepository = placeRepository,
                            savedPlaces = savedPlaces,
                            onBack = back,
                            onPlaceSelected = { place ->
                                screen = when {
                                    startFor != null -> startFor.copy(start = place)
                                    // The same place twice is refused by the server (400): keep the list and say so.
                                    stopFor != null -> com.deungbul.anshimgil.data.route.withStop(stopFor.stops, com.deungbul.anshimgil.data.route.StopPlace(place.name, LatLon(place.lat, place.lon)))
                                        ?.let { stopFor.copy(stops = it) }
                                        ?: stopFor.also { android.widget.Toast.makeText(appContext, "${com.deungbul.anshimgil.data.route.eunNeun(place.name)} 이미 들를 곳에 있어요", android.widget.Toast.LENGTH_SHORT).show() }
                                    destFor != null -> destFor.copy(destination = place)
                                    else -> Screen.Route(place)
                                }
                            },
                            modifier = modifier,
                            placeholder = when {
                                startFor != null -> "어디서 출발할까요?"
                                stopFor != null -> "어디를 거쳐 갈까요?"
                                else -> "어디로 갈까요?"
                            },
                            onUseCurrentLocation = startFor?.let { route -> { screen = route.copy(start = null) } },
                        )
                    }
                }
                screen is Screen.Route -> {
                    val route = screen as Screen.Route
                    val back = { screen = if (route.fromHome) Screen.Home else Screen.Search() }
                    BackHandler(onBack = back)
                    val samples by remember(mode) { walkSpeedRepository.samples(mode) }.collectAsStateWithLifecycle(initialValue = emptyList())
                    // key: find the route again whenever the start or destination changes.
                    androidx.compose.runtime.key(route) {
                        RouteScreen(
                            mode = mode,
                            startWithTransit = route.transit,
                            preferred = preferredRoute(usage, serverPreferTransit),
                            autoStart = route.autoStart,
                            voiceOn = settings.voiceGuide,
                            onChosen = { choice -> scope.launch { usageRepository?.chooseRoute(choice) } },
                            startPlace = route.start,
                            destination = route.destination,
                            stops = route.stops,
                            routeRepository = routeRepository,
                            placeRepository = placeRepository,
                            autoConditions = settings.autoConditions,
                            onBack = back,
                            onPickStart = { screen = Screen.Search(startFor = route) },
                            onPickDestination = { screen = Screen.Search(destFor = route) },
                            onAddStop = { screen = Screen.Search(stopFor = route) },
                            onRemoveStop = { i -> screen = route.copy(stops = route.stops.filterIndexed { j, _ -> j != i }) },
                            onSwap = { from ->
                                val fromPlace = route.start
                                    ?: Place("here", from.label, "", from.point.lat, from.point.lon, null)
                                screen = Screen.Route(destination = fromPlace, start = route.destination, fromHome = route.fromHome, stops = route.stops.reversed())
                            },
                            onChangeDestination = { place -> screen = route.copy(destination = place) },
                            walkSpeedMPerMin = personalSpeed(mode, samples),
                            onStart = { result, type, arriveAt -> screen = Screen.Guide(route, result, type, arriveAt) },
                            facilityRepository = facilityRepository,
                            onStartTransit = { t -> screen = Screen.TransitGuide(route, t) },
                            modifier = modifier,
                        )
                    }
                }
                screen is Screen.TransitGuide -> {
                    val guide = screen as Screen.TransitGuide
                    BackHandler { screen = guide.route }
                    LaunchedEffect(guide) { guideArrived = false; startTrip(guide.route.destination, transit = true, result = null, type = null, mode = mode) }
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    TransitGuideScreen(
                        onArrived = { guideArrived = true },
                        onLive = { fromUser, text -> assistantChat.note(fromUser, text) },
                        onOpenAssistant = openAssistantOverGuide,
                        mode = mode,
                        route = guide.transit,
                        destinationName = guide.route.destination.name,
                        voiceByDefault = settings.voiceGuide,
                        facilityRepository = facilityRepository,
                        guardians = settings.guardians,
                        showTurnSigns = settings.turnSigns,
                        showSos = settings.sosButton,
                        routeRepository = routeRepository,
                        walkSpeedRepository = walkSpeedRepository,
                        onFinish = { finishTrip(arrived = guideArrived); screen = Screen.Home },
                        modifier = modifier,
                    )
                    if (assistantOverGuide) {
                        BackHandler { assistantOverGuide = false }
                        assistantPanel(mode) { assistantOverGuide = false }
                    }
                    reportPanel(mode)
                    }
                }
                screen is Screen.Guide -> {
                    val guide = screen as Screen.Guide
                    BackHandler { GuideStore.clear(appContext); screen = guide.route }
                    LaunchedEffect(guide) {
                        guideArrived = false
                        if (!guide.resumed) startTrip(guide.route.destination, transit = false, result = guide.result, type = guide.type, mode = mode)
                        // Kept until the guidance ends, so it goes on if Android closes the app meanwhile.
                        val path = (guide.result.route(guide.type) ?: guide.result.routes.first()).path
                        if (path.isNotEmpty()) GuideStore.save(
                            appContext,
                            SavedGuide(guide.route.destination, guide.route.start, guide.route.stops, guide.type, guide.arriveAt, path.first(), path.last(), System.currentTimeMillis()),
                        )
                    }
                    DisposableEffect(Unit) { onGuiding(true); onDispose { onGuiding(false) } }
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    GuideScreen(
                        onArrivedChange = { guideArrived = it },
                        onLive = { fromUser, text -> assistantChat.note(fromUser, text) },
                        onOpenAssistant = openAssistantOverGuide,
                        onOpenReport = { reportPrefill = null; reportOverGuide = true },
                        reportedCount = reportedInGuide,
                        mode = mode,
                        result = guide.result,
                        type = guide.type,
                        destinationName = guide.arriveAt ?: guide.route.destination.name,
                        voiceByDefault = settings.voiceGuide,
                        facilityRepository = facilityRepository,
                        guardians = settings.guardians,
                        showTurnSigns = settings.turnSigns,
                        showSos = settings.sosButton,
                        routeRepository = routeRepository,
                        walkSpeedRepository = walkSpeedRepository,
                        onFinish = { GuideStore.clear(appContext); finishTrip(arrived = guideArrived); screen = Screen.Home },
                        modifier = modifier,
                        compact = inPip,
                        resumed = guide.resumed,
                    )
                    // The small window (PIP) shows the guidance alone.
                    if (assistantOverGuide && !inPip) {
                        BackHandler { assistantOverGuide = false }
                        assistantPanel(mode) { assistantOverGuide = false }
                    }
                    if (!inPip) reportPanel(mode)
                    }
                }
                else -> HomeScreen(
                    mode = mode,
                    saved = savedFavorites,
                    extraMapItems = settings.extraMapItems,
                    facilityRepository = facilityRepository,
                    placeRepository = placeRepository,
                    onOpenSearch = { screen = Screen.Search() },
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenReport = { reportPrefill = null; screen = Screen.Report },
                    onPlaceSelected = { place -> screen = Screen.Route(place, fromHome = true) },
                    guardians = settings.guardians,
                    showSos = settings.sosButton,
                    modifier = modifier,
                    onOpenAssistant = { busesOnHome = false; assistantOpening = null; assistantListen = false; screen = Screen.Assistant },
                    hintSource = mapHints,
                    onAskAssistant = { line -> busesOnHome = false; assistantOpening = line; assistantListen = false; screen = Screen.Assistant },
                    startWithBuses = busesOnHome,
                    onBusesShown = { busesOnHome = false },
                    startWithSos = sosOnHome,
                    onSosShown = { sosOnHome = false },
                    askTripConsent = askTripConsent,
                    onTripConsent = { yes ->
                        askTripConsent = false
                        if (!yes) assistantChat.note(false, com.deungbul.anshimgil.data.user.CONSENT_NO_REPLY)
                        scope.launch { tripLog?.setConsent(if (yes) TripConsent.YES else TripConsent.NO, mode) }
                    },
                    usage = usage,
                    adaptation = if (askTripConsent || setupStep != null) null else adaptationToOffer(usage),
                    setupAsk = setupStep?.let { setupQuestion(it, setupAnswers) to it.answers },
                    setupFromSpeech = { said -> setupStep?.let { setupAnswerFromSpeech(it, said) } },
                    setupByVoice = setupAnswers.byVoice,
                    undoAsk = homeUndo?.first,
                    feedbackAsk = tempAsk?.let { it to com.deungbul.anshimgil.data.user.YES_NO } ?: feedbackStep?.let { it.question to it.answers } ?: learnedSay?.let { it to listOf("알겠어요") },
                    onFeedbackAnswer = feedback@{ i ->
                        if (tempAsk != null) {
                            tempAsk = null
                            val st = com.deungbul.anshimgil.data.user.TempModeStore.get(appContext)
                            if (i == 0 && st != null) scope.launch { endTempMode(st) }
                            return@feedback
                        }
                        val step = feedbackStep
                        if (step == null) {
                            learnedSay = null
                        } else {
                            val trip = feedbackTrip
                            feedbackStep = nextFeedbackStep(step, i)
                            val send = feedbackFor(step, i)
                            if (feedbackStep == null) feedbackTrip = null
                            if (send != null && trip != null) scope.launch {
                                val says = tripLog?.feedback(trip.first, true, trip.second, send.first, send.second).orEmpty()
                                learnedSay = says.firstOrNull()
                                if (says.isEmpty()) assistantChat.note(false, if (send.first == "good") "다행이에요. 다음에도 편한 길로 안내할게요." else "알려 주셔서 고마워요. 다음 길을 고를 때 참고할게요.")
                            }
                        }
                    },
                    onUndoAnswer = { undo ->
                        val u = homeUndo
                        homeUndo = null
                        if (undo && u != null) {
                            scope.launch { u.second() }
                            assistantChat.note(false, "되돌렸어요.")
                        }
                    },
                    onSetupAnswer = { i ->
                        val step = setupStep
                        if (step == SetupStep.DONE && i == SETUP_UNDO_INDEX) {
                            // "처음대로 되돌리기": everything as it was before the questions.
                            setupStep = null
                            setupBefore?.let { b ->
                                scope.launch {
                                    settingsRepository.setTextSize(b.settings.textSize)
                                    usageRepository?.setTextBoost(b.usage.textBoost)
                                    settingsRepository.setVoiceGuide(b.settings.voiceGuide)
                                    settingsRepository.setSosButton(b.settings.sosButton)
                                }
                            }
                            assistantChat.note(false, "처음대로 되돌렸어요. 필요하면 설정에서나 저에게 말해서 바꿀 수 있어요.")
                        } else if (step != null) {
                            if ((step == SetupStep.START || step == SetupStep.CHANGED) && i == 0) setupBefore = SettingsSnapshot(mode, settings, usage)
                            val (next, answers) = answerSetup(step, i, setupAnswers, settings.textSize)
                            setupAnswers = answers
                            setupStep = next
                            // The server's setup answers go to its user_profile; the trip-record answer as consent.
                            if (step == SetupStep.CONFIRM && answers.confirmed && answers.aid != null) scope.launch {
                                runCatching {
                                    myInfoRepository.update(mode ?: UserMode.GENERAL, org.json.JSONObject()
                                        .put("mobilityAid", com.deungbul.anshimgil.data.user.aidProfileValue(answers.aid) ?: org.json.JSONObject.NULL)
                                        .put("stairsOk", answers.stairs ?: 2).put("setupDone", true))
                                }
                            }
                            if (step == SetupStep.CONSENT) answers.consent?.let { yes ->
                                if (!yes) assistantChat.note(false, com.deungbul.anshimgil.data.user.CONSENT_NO_REPLY)
                                scope.launch { tripLog?.setConsent(if (yes) TripConsent.YES else TripConsent.NO, mode ?: UserMode.GENERAL) }
                            }
                            if ((step == SetupStep.START || step == SetupStep.CHANGED) && next == null) {
                                assistantChat.note(false, "알겠어요. 필요하면 설정에서나 저에게 말해서 언제든 바꿀 수 있어요.")
                            }
                            // Applied at once, so the walker sees the text change.
                            scope.launch {
                                answers.textSize?.let { settingsRepository.setTextSize(it) }
                                answers.textBoost?.let { usageRepository?.setTextBoost(it) }
                                answers.voiceGuide?.let { settingsRepository.setVoiceGuide(it) }
                                answers.sosButton?.let { settingsRepository.setSosButton(it) }
                            }
                        }
                    },
                    onAdaptation = { a, yes ->
                        val boostBefore = usage.textBoost
                        scope.launch {
                            usageRepository?.answer(a, yes)
                        }
                        // A yes shows at once what changed, with 되돌리기 next to it (사용성 점검표 21번).
                        if (yes && a != Adaptation.CALENDAR) homeUndo = "글씨를 한 단계 크게 바꿨어요." to { usageRepository?.setTextBoost(boostBefore) }
                        if (a == Adaptation.CALENDAR && yes) useCalendar(true)
                    },
                    onNearbyTap = { k -> scope.launch { usageRepository?.tapNearby(k) } },
                    onPress = { missed -> scope.launch { usageRepository?.press(missed) } },
                    onZoomIn = { scope.launch { usageRepository?.zoomIn() } },
                    onStartNow = { place, transit -> screen = Screen.Route(place, fromHome = true, transit = transit, autoStart = true) },
                    onLive = { fromUser, text -> assistantChat.note(fromUser, text) },
                )
            }
        }
    }
}

