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
import com.deungbul.anshimgil.data.AssistantContext
import com.deungbul.anshimgil.ui.guide.TransitGuideScreen
import com.deungbul.anshimgil.data.TransitRoute
import com.deungbul.anshimgil.data.EventRepository
import com.deungbul.anshimgil.data.MapHintSource
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.location.currentLocation
import kotlinx.coroutines.flow.first
import com.deungbul.anshimgil.ui.assistant.AssistantScreen
import com.deungbul.anshimgil.ui.theme.SeipjidoTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val modeRepository = ModeRepository(applicationContext)
        val savedPlaces = SavedPlacesRepository(applicationContext)
        val placeRepository = PlaceRepository.create()
        val facilityRepository = FacilityRepository.create()
        val routeRepository = RouteRepository.create()
        val settingsRepository = SettingsRepository(applicationContext)
        val walkSpeedRepository = WalkSpeedRepository(applicationContext)
        // Walks that could not be sent last time (offline, server not ready) go now.
        lifecycleScope.launch { runCatching { walkSpeedRepository.uploadPending() } }
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            SeipjidoTheme {
                ScaledText(settings.textSize) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        AppRoot(
                            modeRepository, savedPlaces, placeRepository, facilityRepository, routeRepository,
                            settingsRepository, settings, walkSpeedRepository, Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

/** Applies the text size chosen in settings on top of the phone's own font size. */
@Composable
private fun ScaledText(size: TextSize, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * size.scale),
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
    /** 불편 신고 (FR-49). */
    data object Report : Screen

    /** 말로 하기 on the map screen. */
    data object Assistant : Screen
    /** [start] null means the phone's position. [fromHome]: opened from a home shortcut, so back returns home. */
    data class Route(val destination: Place, val start: Place? = null, val fromHome: Boolean = false) : Screen
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
) {
    val savedFlow = remember(modeRepository) { modeRepository.mode.map { SavedMode.Ready(it) } }
    val saved by savedFlow.collectAsStateWithLifecycle(initialValue = SavedMode.Loading)
    var changingMode by rememberSaveable { mutableStateOf(false) }
    // 말로 맞추기 open on top of the mode picker.
    var talking by rememberSaveable { mutableStateOf(false) }
    val setupAssistant = remember { SetupAssistant.create() }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val appAssistant = remember {
        AppAssistant.create(
            guestId = { walkSpeedRepository.guestId() },
            favorites = {
                savedPlaces.favorites.first().mapNotNull { f -> f.place?.let { f.name to LatLon(it.lat, it.lon) } }
            },
        )
    }
    // The assistant asked the map to open the nearby-bus card.
    var busesOnHome by remember { mutableStateOf(false) }
    // AI 비서 opened from its bubble on the map starts with that line.
    var assistantOpening by remember { mutableStateOf<String?>(null) }
    val mapHints = remember {
        MapHintSource(
            EventRepository.create(),
            guestId = { walkSpeedRepository.guestId() },
            favorites = {
                savedPlaces.favorites.first().mapNotNull { f -> f.place?.let { f.name to LatLon(it.lat, it.lon) } }
            },
        )
    }
    // The assistant's 보호자에게 알리기: open SOS on the map.
    var sosOnHome by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()

    when (val state = saved) {
        SavedMode.Loading -> Box(modifier.fillMaxSize())
        is SavedMode.Ready -> {
            val mode = state.mode
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
                            scope.launch {
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
                    AssistantScreen(
                        opening = assistantOpening,
                        assistant = appAssistant,
                        context = AssistantContext(mode, settings, where = { currentLocation(appContext, 4_000) }),
                        placeRepository = placeRepository,
                        onChangeSettings = { change ->
                            scope.launch {
                                change.mode?.let { modeRepository.save(it) }
                                change.textSize?.let { settingsRepository.setTextSize(it) }
                                change.voiceGuide?.let { settingsRepository.setVoiceGuide(it) }
                                change.sosButton?.let { settingsRepository.setSosButton(it) }
                                change.addMapItems.forEach { settingsRepository.setExtraMapItem(it, true) }
                                change.removeMapItems.forEach { settingsRepository.setExtraMapItem(it, false) }
                            }
                        },
                        onRoute = { place -> screen = Screen.Route(place, fromHome = true) },
                        onShowBuses = { busesOnHome = true; screen = Screen.Home },
                        onOpenReport = { screen = Screen.Report },
                        onAlertGuardian = { sosOnHome = true; screen = Screen.Home },
                        onBack = { screen = Screen.Home },
                        modifier = modifier,
                    )
                }
                screen == Screen.Report -> {
                    BackHandler { screen = Screen.Home }
                    ReportScreen(mode = mode, onBack = { screen = Screen.Home }, modifier = modifier)
                }
                screen == Screen.Settings -> {
                    BackHandler { screen = Screen.Home }
                    SettingsScreen(
                        mode = mode,
                        settings = settings,
                        onBack = { screen = Screen.Home },
                        onChangeMode = { changingMode = true },
                        onManagePlaces = { screen = Screen.Search() },
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
                    TransitGuideScreen(
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
                        onFinish = { screen = Screen.Home },
                        modifier = modifier,
                    )
                }
                screen is Screen.Guide -> {
                    val guide = screen as Screen.Guide
                    BackHandler { screen = guide.route }
                    GuideScreen(
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
                        onFinish = { screen = Screen.Home },
                        modifier = modifier,
                    )
                }
                else -> HomeScreen(
                    mode = mode,
                    extraMapItems = settings.extraMapItems,
                    facilityRepository = facilityRepository,
                    placeRepository = placeRepository,
                    onOpenSearch = { screen = Screen.Search() },
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenReport = { screen = Screen.Report },
                    onPlaceSelected = { place -> screen = Screen.Route(place, fromHome = true) },
                    guardians = settings.guardians,
                    showSos = settings.sosButton,
                    modifier = modifier,
                    onOpenAssistant = { busesOnHome = false; assistantOpening = null; screen = Screen.Assistant },
                    hintSource = mapHints,
                    onAskAssistant = { line -> busesOnHome = false; assistantOpening = line; screen = Screen.Assistant },
                    startWithBuses = busesOnHome,
                    onBusesShown = { busesOnHome = false },
                    startWithSos = sosOnHome,
                    onSosShown = { sosOnHome = false },
                )
            }
        }
    }
}
