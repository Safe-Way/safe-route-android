package com.deungbul.anshimgil.map

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView

private const val TAG = "KakaoMapView"

/** Chosun University, the demo area. */
val CHOSUN_UNIV: LatLng = LatLng.from(35.1404, 126.9318)

/**
 * Kakao map (a classic View) wrapped for Compose.
 * The map follows the screen's lifecycle so it pauses in the background.
 */
@Composable
fun KakaoMapView(
    modifier: Modifier = Modifier,
    center: LatLng = CHOSUN_UNIV,
    zoomLevel: Int = 16,
    onMapReady: (KakaoMap) -> Unit = {},
    onMapError: (Exception) -> Unit = {},
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    Log.d(TAG, "map destroyed")
                }

                override fun onMapError(error: Exception) {
                    // An auth error here usually means the key or key hash is not registered.
                    Log.e(TAG, "map error", error)
                    onMapError(error)
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(kakaoMap: KakaoMap) {
                    onMapReady(kakaoMap)
                }

                override fun getPosition(): LatLng = center

                override fun getZoomLevel(): Int = zoomLevel
            },
        )

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
