package com.deungbul.anshimgil.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.hypot

/** The key of the page while the app is still loading: the cover stays on. */
const val LOADING_PAGE = "loading"

private val Cover = Color(0xFFF7FAF6).copy(alpha = 0.95f)

/**
 * 화면 전환 '작은 원' (11/8, the team's pick of five tried): when the page changes, a light cover over the new
 * page opens as a growing circle from where the walker last tapped ([origin]); at the start of the app, from the
 * middle. It is drawn over everything, also over the map (which is drawn in its own surface and cannot be faded
 * or clipped itself), and never takes a touch, so nothing waits for it.
 */
@Composable
fun PageReveal(pageKey: String, origin: Offset?, modifier: Modifier = Modifier) {
    val open = remember { Animatable(if (pageKey == LOADING_PAGE) 0f else 1f) }
    var previous by remember { mutableStateOf<String?>(null) }
    var launch by remember { mutableStateOf(pageKey == LOADING_PAGE) }
    LaunchedEffect(pageKey) {
        val before = previous
        previous = pageKey
        if (pageKey == LOADING_PAGE) {
            open.snapTo(0f)
            return@LaunchedEffect
        }
        if (before == null) return@LaunchedEffect
        launch = before == LOADING_PAGE
        open.snapTo(0f)
        open.animateTo(1f, tween(if (launch) 450 else 320, easing = FastOutSlowInEasing))
    }
    val v = open.value
    if (v >= 1f) return
    Canvas(modifier) {
        val at = if (launch || origin == null) Offset(size.width / 2, size.height / 2) else origin
        val farthest = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .maxOf { hypot(it.x - at.x, it.y - at.y) }
        val r = farthest * v
        val cover = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            if (r > 0f) addOval(Rect(at, r))
        }
        drawPath(cover, Cover)
    }
}
