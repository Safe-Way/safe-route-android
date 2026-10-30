package com.deungbul.anshimgil

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.ModeRepository
import com.deungbul.anshimgil.data.Place
import com.deungbul.anshimgil.data.PlaceRepository
import com.deungbul.anshimgil.data.RouteRepository
import com.deungbul.anshimgil.data.RouteResult
import com.deungbul.anshimgil.data.RouteType
import com.deungbul.anshimgil.ui.guide.GuideScreen
import com.deungbul.anshimgil.map.CHOSUN_UNIV
import com.deungbul.anshimgil.ui.route.RouteScreen
import com.deungbul.anshimgil.data.SavedPlacesRepository
import com.deungbul.anshimgil.data.UserMode
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
        setContent {
            SeipjidoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modeRepository, savedPlaces, placeRepository, routeRepository, Modifier.padding(innerPadding))
                }
            }
        }
    }
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
    data class Route(val destination: Place) : Screen
    data class Guide(val destination: Place, val result: RouteResult, val type: RouteType) : Screen
}

/** Where routes start until GPS is wired in (#7): the Chosun University demo area. */
private val DEMO_ORIGIN = LatLon(CHOSUN_UNIV.latitude, CHOSUN_UNIV.longitude)

/** First launch shows the mode picker; after that the map opens directly. */
@Composable
fun AppRoot(
    modeRepository: ModeRepository,
    savedPlaces: SavedPlacesRepository,
    placeRepository: PlaceRepository,
    routeRepository: RouteRepository,
    modifier: Modifier = Modifier,
) {
    val savedFlow = remember(modeRepository) { modeRepository.mode.map { SavedMode.Ready(it) } }
    val saved by savedFlow.collectAsStateWithLifecycle(initialValue = SavedMode.Loading)
    var changingMode by rememberSaveable { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                        origin = DEMO_ORIGIN,
                        destination = destination,
                        routeRepository = routeRepository,
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
                        onFinish = { screen = Screen.Home },
                        modifier = modifier,
                    )
                }
                else -> HomeScreen(
                    mode = mode,
                    onOpenSearch = { screen = Screen.Search },
                    // Opens the mode picker until the settings screen (#8) exists.
                    onOpenSettings = { changingMode = true },
                    modifier = modifier,
                )
            }
        }
    }
}
