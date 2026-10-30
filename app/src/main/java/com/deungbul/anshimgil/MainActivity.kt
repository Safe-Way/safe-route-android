package com.deungbul.anshimgil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deungbul.anshimgil.data.ModeRepository
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.map.KakaoMapView
import com.deungbul.anshimgil.ui.mode.ModeSelectScreen
import com.deungbul.anshimgil.ui.mode.style
import com.deungbul.anshimgil.ui.theme.SeipjidoTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val modeRepository = ModeRepository(applicationContext)
        setContent {
            SeipjidoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modeRepository, Modifier.padding(innerPadding))
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

/** First launch shows the mode picker; after that the map opens directly. */
@Composable
fun AppRoot(modeRepository: ModeRepository, modifier: Modifier = Modifier) {
    val savedFlow = remember(modeRepository) { modeRepository.mode.map { SavedMode.Ready(it) } }
    val saved by savedFlow.collectAsStateWithLifecycle(initialValue = SavedMode.Loading)
    var changingMode by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    when (val state = saved) {
        SavedMode.Loading -> Box(modifier.fillMaxSize())
        is SavedMode.Ready -> {
            val mode = state.mode
            if (mode == null || changingMode) {
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
            } else {
                MapScreen(mode = mode, onChangeMode = { changingMode = true }, modifier = modifier)
            }
        }
    }
}

@Composable
fun MapScreen(mode: UserMode, onChangeMode: () -> Unit, modifier: Modifier = Modifier) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        MapMessage("local.properties에 KAKAO_NATIVE_APP_KEY를 넣고 다시 빌드해 주세요.", modifier)
        return
    }

    var error by remember { mutableStateOf<String?>(null) }
    val style = mode.style()
    Box(modifier = modifier.fillMaxSize()) {
        KakaoMapView(
            modifier = Modifier.fillMaxSize(),
            onMapError = { error = "지도를 불러오지 못했어요. 키와 키 해시 등록을 확인해 주세요.\n(${it.message})" },
        )
        // Current mode; tapping it reopens the picker until the settings screen (#8) exists.
        Surface(
            onClick = onChangeMode,
            shape = RoundedCornerShape(20.dp),
            color = style.accent,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .heightIn(min = 44.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("${style.label} 모드 · 바꾸기", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        error?.let { MapMessage(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun MapMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
