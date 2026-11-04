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
import com.deungbul.anshimgil.data.AppSettings
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
import com.deungbul.anshimgil.ui.settings.SettingsScreen
import com.deungbul.anshimgil.ui.search.SearchScreen
import com.deungbul.anshimgil.ui.common.MapMessage
import com.deungbul.anshimgil.ui.home.HomeScreen
import com.deungbul.anshimgil.ui.mode.ModeSelectScreen
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
        val routeRepository = RouteRepository.create()
        val settingsRepository = SettingsRepository(applicationContext)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            SeipjidoTheme {
                ScaledText(settings.textSize) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        AppRoot(
                            modeRepository, savedPlaces, placeRepository, routeRepository,
                            settingsRepository, settings, Modifier.padding(innerPadding),
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
    data object Search : Screen
    data object Settings : Screen
    data class Route(val destination: Place) : Screen
    data class Guide(val destination: Place, val result: RouteResult, val type: RouteType) : Screen
}

/** First launch shows the mode picker; after that the map opens directly. */
@Composable
fun AppRoot(
    modeRepository: ModeRepository,
    savedPlaces: SavedPlacesRepository,
    placeRepository: PlaceRepository,
    routeRepository: RouteRepository,
    settingsRepository: SettingsRepository,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val savedFlow = remember(modeRepository) { modeRepository.mode.map { SavedMode.Ready(it) } }
    val saved by savedFlow.collectAsStateWithLifecycle(initialValue = SavedMode.Loading)
    var changingMode by rememberSaveable { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()

    when (val state = saved) {
        SavedMode.Loading -> Box(modifier.fillMaxSize())
        is SavedMode.Ready -> {
            val mode = state.mode
            when {
                mode == null || changingMode -> {
                    if (mode != null) BackHandler { changingMode = false }
                    ModeSelectScreen(
                        initial = mode,
                        onStart = { picked ->
                            scope.launch {
                                modeRepository.save(picked)
                                changingMode = false
                            }
                        },
                        modifier = modifier,
                    )
                }
                BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank() ->
                    MapMessage("local.properties에 KAKAO_NATIVE_APP_KEY를 넣고 다시 빌드해 주세요.", modifier)
                screen == Screen.Settings -> {
                    BackHandler { screen = Screen.Home }
                    SettingsScreen(
                        mode = mode,
                        settings = settings,
                        onBack = { screen = Screen.Home },
                        onChangeMode = { changingMode = true },
                        onManagePlaces = { screen = Screen.Search },
                        onVoiceChange = { scope.launch { settingsRepository.setVoiceGuide(it) } },
                        onAutoConditionsChange = { scope.launch { settingsRepository.setAutoConditions(it) } },
                        onTextSizeChange = { scope.launch { settingsRepository.setTextSize(it) } },
                        modifier = modifier,
                    )
                }
                screen == Screen.Search -> {
                    BackHandler { screen = Screen.Home }
                    SearchScreen(
                        placeRepository = placeRepository,
                        savedPlaces = savedPlaces,
                        onBack = { screen = Screen.Home },
                        onPlaceSelected = { place -> screen = Screen.Route(place) },
                        modifier = modifier,
                    )
                }
                screen is Screen.Route -> {
                    val destination = (screen as Screen.Route).destination
                    BackHandler { screen = Screen.Search }
                    RouteScreen(
                        mode = mode,
                        destination = destination,
                        routeRepository = routeRepository,
                        autoConditions = settings.autoConditions,
                        onBack = { screen = Screen.Search },
                        onStart = { result, type -> screen = Screen.Guide(destination, result, type) },
                        modifier = modifier,
                    )
                }
                screen is Screen.Guide -> {
                    val guide = screen as Screen.Guide
                    BackHandler { screen = Screen.Route(guide.destination) }
                    GuideScreen(
                        mode = mode,
                        result = guide.result,
                        type = guide.type,
                        destinationName = guide.destination.name,
                        voiceByDefault = settings.voiceGuide,
                        onFinish = { screen = Screen.Home },
                        modifier = modifier,
                    )
                }
                else -> HomeScreen(
                    mode = mode,
                    onOpenSearch = { screen = Screen.Search },
                    onOpenSettings = { screen = Screen.Settings },
                    modifier = modifier,
                )
            }
        }
    }
}
