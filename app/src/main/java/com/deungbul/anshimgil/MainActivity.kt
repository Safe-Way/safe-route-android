package com.deungbul.anshimgil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deungbul.anshimgil.map.KakaoMapView
import com.deungbul.anshimgil.ui.theme.SeipjidoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeipjidoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        MapMessage("local.properties에 KAKAO_NATIVE_APP_KEY를 넣고 다시 빌드해 주세요.", modifier)
        return
    }

    var error by remember { mutableStateOf<String?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
        KakaoMapView(
            modifier = Modifier.fillMaxSize(),
            onMapError = { error = "지도를 불러오지 못했어요. 키와 키 해시 등록을 확인해 주세요.\n(${it.message})" },
        )
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
