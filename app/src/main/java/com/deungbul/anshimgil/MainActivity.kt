package com.deungbul.anshimgil

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
import com.deungbul.anshimgil.data.AppSettings
import com.deungbul.anshimgil.data.FacilityRepository
import com.deungbul.anshimgil.data.ModeRepository
import com.deungbul.anshimgil.data.Place
import com.deungbul.anshimgil.data.PlaceRepository
import com.deungbul.anshimgil.data.RouteRepository
import com.deungbul.anshimgil.data.RouteResult
import com.deungbul.anshimgil.data.RouteType
import com.deungbul.anshimgil.ui.guide.GuideScreen
import com.deungbul.anshimgil.ui.route.RouteScreen
import com.deungbul.anshimgil.data.SavedPlacesRepository
import com.deungbul.anshimgil.data.SettingsRepository
import com.deungbul.anshimgil.data.TextSize
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.data.WalkSpeedRepository
import com.deungbul.anshimgil.data.personalSpeed
import com.deungbul.anshimgil.ui.settings.SettingsScreen
import com.deungbul.anshimgil.ui.report.ReportScreen
import com.deungbul.anshimgil.ui.search.SearchScreen
import com.deungbul.anshimgil.ui.common.MapMessage
import com.deungbul.anshimgil.ui.home.HomeScreen
import com.deungbul.anshimgil.ui.mode.ModeSelectScreen
import com.deungbul.anshimgil.ui.mode.SetupChatScreen
import com.deungbul.anshimgil.data.SetupAssistant
import com.deungbul.anshimgil.data.AppAssistant
import com.deungbul.anshimgil.data.AssistantAction
import androidx.compose.material.icons.filled.AutoAwesome
import com.deungbul.anshimgil.data.AppOpenLog
import com.deungbul.anshimgil.data.AssistantContext
import com.deungbul.anshimgil.location.lastKnownLocation
import com.deungbul.anshimgil.data.Adaptation
import com.deungbul.anshimgil.data.adaptationToOffer
import com.deungbul.anshimgil.data.preferredRoute
import com.deungbul.anshimgil.data.ShownHints
import com.deungbul.anshimgil.data.SETUP_UNDO_INDEX
import com.deungbul.anshimgil.data.nextFeedbackStep
import com.deungbul.anshimgil.data.feedbackFor
import com.deungbul.anshimgil.data.FeedbackStep
import com.deungbul.anshimgil.data.setupAnswerFromSpeech
import com.deungbul.anshimgil.data.SetupStep
import com.deungbul.anshimgil.data.SetupAnswers
import com.deungbul.anshimgil.data.answerSetup
import com.deungbul.anshimgil.data.setupQuestion
import com.deungbul.anshimgil.data.calendarHint
import com.deungbul.anshimgil.data.hasCalendarPermission
import com.deungbul.anshimgil.data.Usage
import com.deungbul.anshimgil.data.UsageRepository
import com.deungbul.anshimgil.data.tripWeather
import com.deungbul.anshimgil.data.TripConsent
import com.deungbul.anshimgil.data.TripLogRepository
import androidx.compose.ui.unit.dp
import com.deungbul.anshimgil.ui.assistant.AssistantChat
import com.deungbul.anshimgil.ui.guide.TransitGuideScreen
import com.deungbul.anshimgil.data.TransitRoute
import com.deungbul.anshimgil.data.EventRepository
import com.deungbul.anshimgil.data.MapHintSource
import com.deungbul.anshimgil.data.LatLon
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

/** What was set before a change, to take it back ([mode] is null before one is picked). */
data class SettingsSnapshot(val mode: UserMode?, val settings: AppSettings, val usage: Usage)

class MainActivity : ComponentActivity() {
    /** A place to open from the 출발 시간 알림 (name, point, by transit), handled once by AppRoot. */
    private val reminded = androidx.compose.runtime.mutableStateOf<Pair<Place, Boolean>?>(null)

    private fun readReminder(intent: android.content.Intent?) {
        val name = intent?.getStringExtra(EXTRA_GO_NAME) ?: return
        val lat = intent.getDoubleExtra(EXTRA_GO_LAT, Double.NaN)
        val lon = intent.getDoubleExtra(EXTRA_GO_LON, Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return
        reminded.value = Place(null, name, "", lat, lon, null) to intent.getBooleanExtra(EXTRA_GO_TRANSIT, false)
        intent.removeExtra(EXTRA_GO_NAME)
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
        val routeRepository = RouteRepository.create(guestId = { walkSpeedRepository.guestId() })
        val tripLog = TripLogRepository(applicationContext, guestId = { walkSpeedRepository.guestId() })
        val myInfoRepository = com.deungbul.anshimgil.data.MyInfoRepository(guestId = { walkSpeedRepository.guestId() })
        val usageRepository = UsageRepository(applicationContext)
        // Walks that could not be sent last time (offline, server not ready) go now.
        lifecycleScope.launch { runCatching { walkSpeedRepository.uploadPending() } }
        // The mode next to the clock while the app is in front; taken away in onStop.
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                modeRepository.mode.collect { mode -> if (mode != null) ModeStatus.show(this@MainActivity, mode) else ModeStatus.hide(this@MainActivity) }
            }
        }
        setContent {
            // Asked once (Android 13+), after a mode is chosen, for the mode icon next to the clock.
            val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) lifecycleScope.launch { modeRepository.mode.first()?.let { ModeStatus.show(this@MainActivity, it) } }
            }
            val hasMode by modeRepository.mode.map { it != null }.collectAsStateWithLifecycle(initialValue = false)
            LaunchedEffect(hasMode) {
                val prefs = getSharedPreferences("app", MODE_PRIVATE)
                if (hasMode && android.os.Build.VERSION.SDK_INT >= 33 && !ModeStatus.canShow(this@MainActivity) &&
                    !prefs.getBoolean("askedNotify", false)
                ) {
                    prefs.edit().putBoolean("askedNotify", true).apply()
                    notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
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
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
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
    data class Search(val startFor: Route? = null) : Screen
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
    ) : Screen
    data class Guide(val route: Route, val result: RouteResult, val type: RouteType) : Screen
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
    myInfoRepository: com.deungbul.anshimgil.data.MyInfoRepository = com.deungbul.anshimgil.data.MyInfoRepository(guestId = { walkSpeedRepository.guestId() }),
    usageRepository: UsageRepository? = null,
    /** From the 출발 시간 알림: open the route there. */
    reminded: Pair<Place, Boolean>? = null,
    onRemindedHandled: () -> Unit = {},
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
    val setupAssistant = remember { SetupAssistant.create() }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
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
    // AI 비서's conversation: kept while the app runs, gone when it is closed.
    val assistantChat = remember { AssistantChat() }
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
        val (place, transit) = reminded ?: return@LaunchedEffect
        screen = Screen.Route(place, fromHome = true, transit = transit)
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
                if (com.deungbul.anshimgil.data.ServerApi.isConfigured) runCatching { myInfoRepository.syncMode(m) }
            }
            // AI 비서, as its own screen from the map or laid over walking guidance (which keeps running underneath).
            val assistantPanel: @Composable (UserMode, () -> Unit) -> Unit = { mode, onClose ->
                    AssistantScreen(
                        opening = assistantOpening,
                        listenAtStart = assistantListen,
                        chat = assistantChat,
                        assistant = appAssistant,
                        context = AssistantContext(mode, settings, usage = usage, where = { currentLocation(appContext, 4_000) }),
                        placeRepository = placeRepository,
                        onChangeSettings = { change ->
                            val before = SettingsSnapshot(mode, settings, usage)
                            assistantUndo = {
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
                        onShowBuses = { busesOnHome = true; screen = Screen.Home },
                        onOpenReport = { r -> reportPrefill = r; screen = Screen.Report },
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
                            if (mode == null) setupStep = SetupStep.START
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
                    val back = { screen = startFor ?: Screen.Home }
                    BackHandler(onBack = back)
                    // key: a fresh search box when switching between destination and start.
                    androidx.compose.runtime.key(startFor) {
                        SearchScreen(
                            placeRepository = placeRepository,
                            savedPlaces = savedPlaces,
                            onBack = back,
                            onPlaceSelected = { place ->
                                screen = startFor?.copy(start = place) ?: Screen.Route(place)
                            },
                            modifier = modifier,
                            placeholder = if (startFor != null) "어디서 출발할까요?" else "어디로 갈까요?",
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
                            preferred = preferredRoute(usage),
                            autoStart = route.autoStart,
                            voiceOn = settings.voiceGuide,
                            onChosen = { choice -> scope.launch { usageRepository?.chooseRoute(choice) } },
                            startPlace = route.start,
                            destination = route.destination,
                            routeRepository = routeRepository,
                            placeRepository = placeRepository,
                            autoConditions = settings.autoConditions,
                            onBack = back,
                            onPickStart = { screen = Screen.Search(startFor = route) },
                            onSwap = { from ->
                                val fromPlace = route.start
                                    ?: Place("here", from.label, "", from.point.lat, from.point.lon, null)
                                screen = Screen.Route(destination = fromPlace, start = route.destination, fromHome = route.fromHome)
                            },
                            onChangeDestination = { place -> screen = route.copy(destination = place) },
                            walkSpeedMPerMin = personalSpeed(mode, samples),
                            onStart = { result, type -> screen = Screen.Guide(route, result, type) },
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
                    }
                }
                screen is Screen.Guide -> {
                    val guide = screen as Screen.Guide
                    BackHandler { screen = guide.route }
                    LaunchedEffect(guide) { guideArrived = false; startTrip(guide.route.destination, transit = false, result = guide.result, type = guide.type, mode = mode) }
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    GuideScreen(
                        onArrivedChange = { guideArrived = it },
                        onLive = { fromUser, text -> assistantChat.note(fromUser, text) },
                        onOpenAssistant = openAssistantOverGuide,
                        mode = mode,
                        result = guide.result,
                        type = guide.type,
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
                        if (!yes) assistantChat.note(false, com.deungbul.anshimgil.data.CONSENT_NO_REPLY)
                        scope.launch { tripLog?.setConsent(if (yes) TripConsent.YES else TripConsent.NO, mode) }
                    },
                    usage = usage,
                    adaptation = if (askTripConsent || setupStep != null) null else adaptationToOffer(usage),
                    setupAsk = setupStep?.let { setupQuestion(it, setupAnswers) to it.answers },
                    setupFromSpeech = { said -> setupStep?.let { setupAnswerFromSpeech(it, said) } },
                    undoAsk = homeUndo?.first,
                    feedbackAsk = feedbackStep?.let { it.question to it.answers } ?: learnedSay?.let { it to listOf("알겠어요") },
                    onFeedbackAnswer = { i ->
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

