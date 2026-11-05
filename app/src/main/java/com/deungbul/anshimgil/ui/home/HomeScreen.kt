package com.deungbul.anshimgil.ui.home

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
import com.deungbul.anshimgil.data.FakeFacilities
import com.deungbul.anshimgil.data.Place
import com.deungbul.anshimgil.data.PlaceRepository
import com.deungbul.anshimgil.data.UserMode
import com.deungbul.anshimgil.data.chooseOrigin
import com.deungbul.anshimgil.location.currentLocation
import com.deungbul.anshimgil.map.KakaoMapView
import com.deungbul.anshimgil.map.showLayers
import com.deungbul.anshimgil.ui.common.MapMessage
import com.deungbul.anshimgil.ui.mode.style
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import kotlinx.coroutines.launch
import com.kakao.vectormap.camera.CameraUpdateFactory

private val Ink = Color(0xFF17211B)

/** One-tap shortcuts: search word, button text and icon. */
private val NEARBY = listOf(
    Triple("편의점", "편의점", Icons.Filled.Store),
    Triple("파출소", "파출소", Icons.Filled.LocalPolice),
    Triple("화장실", "화장실", Icons.Filled.Wc),
)

/**
 * Home map (issue #3): a plain 2D map that shows only what the chosen mode needs,
 * plus buttons that find the route to the nearest store, police box or toilet.
 */
@Composable
fun HomeScreen(
    mode: UserMode,
    extraMapItems: Set<String>,
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
    val scope = rememberCoroutineScope()

    fun goToNearest(query: String) {
        if (finding != null) return
        finding = query
        nearbyMessage = null
        scope.launch {
            val here = chooseOrigin(currentLocation(context)).point
            val nearest = try {
                placeRepository.search(query, here.lat, here.lon).minByOrNull { it.distanceM ?: Int.MAX_VALUE }
            } catch (e: ApiException) {
                nearbyMessage = e.message
                null
            }
            finding = null
            if (nearest != null) onPlaceSelected(nearest)
            else if (nearbyMessage == null) nearbyMessage = "가까운 ${query}을(를) 찾지 못했어요"
        }
    }

    LaunchedEffect(map, mode, extraMapItems) {
        map?.showLayers(context, FakeFacilities.layersFor(mode, extraMapItems))
    }

    Box(modifier = modifier.fillMaxSize()) {
        KakaoMapView(
            modifier = Modifier.fillMaxSize(),
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
                        onClick = { goToNearest(query) },
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
            onClick = {
                scope.launch {
                    val p = chooseOrigin(currentLocation(context)).point
                    map?.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(p.lat, p.lon), 16))
                }
            },
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

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp),
        ) {
            Text(
                "시설 정보: 예시 데이터 (서버 연결 전)",
                fontSize = 11.sp,
                color = Color(0xFF4A564E),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
