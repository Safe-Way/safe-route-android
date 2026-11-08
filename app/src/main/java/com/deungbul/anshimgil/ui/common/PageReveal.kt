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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The key of the page while the app is still loading: the reveal stays closed. */
const val LOADING_PAGE = "loading"

private val RevealGreen = Color(0xFF1F6B42)

/**
 * 화면 전환 (11/8): when the page changes, a 세잎지도-green cover is over the screen at once and opens like an iris
 * from where the walker last tapped ([origin]), showing the new page from that point outward. At the start of the
 * app the cover shows a three-leaf mark in the middle first and opens from there.
 *
 * It is drawn over everything, also over the map (which is drawn in its own surface and cannot be faded or
 * clipped itself), and never takes a touch, so nothing waits for it.
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
        // The first page shown without loading first: nothing to reveal.
        if (before == null) return@LaunchedEffect
        launch = before == LOADING_PAGE
        open.snapTo(0f)
        if (launch) delay(280)
        open.animateTo(1f, tween(if (launch) 700 else 430, easing = FastOutSlowInEasing))
    }
    val v = open.value
    if (v >= 1f) return
    Canvas(modifier) {
        val center = if (launch || origin == null) Offset(size.width / 2, size.height / 2) else origin
        val farthest = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .maxOf { hypot(it.x - center.x, it.y - center.y) }
        val r = farthest * v
        val cover = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            if (r > 0f) addOval(Rect(center, r))
        }
        drawPath(cover, RevealGreen)
        // A soft light ring on the opening edge.
        if (r > 0f) drawCircle(Color.White.copy(alpha = 0.45f * (1f - v)), r, center, style = Stroke(4.dp.toPx()))
        if (launch) drawClover(Offset(size.width / 2, size.height / 2), alpha = (1f - v * 2.5f).coerceIn(0f, 1f), growth = 0.85f + 0.15f * (1f - v))
    }
}

/** The 세잎 mark: three round leaves around a point, and a short stem. */
private fun DrawScope.drawClover(at: Offset, alpha: Float, growth: Float) {
    if (alpha <= 0f) return
    val leaf = 17.dp.toPx() * growth
    val reach = 15.dp.toPx() * growth
    val white = Color.White.copy(alpha = alpha)
    listOf(-90.0, 30.0, 150.0).forEach { deg ->
        val a = Math.toRadians(deg)
        drawCircle(white, leaf, Offset(at.x + reach * cos(a).toFloat(), at.y + reach * sin(a).toFloat()))
    }
    drawCircle(RevealGreen.copy(alpha = alpha), 4.dp.toPx() * growth, at)
    val stem = Path().apply {
        moveTo(at.x, at.y + 6.dp.toPx())
        quadraticTo(at.x + 6.dp.toPx(), at.y + 30.dp.toPx() * growth, at.x + 16.dp.toPx(), at.y + 40.dp.toPx() * growth)
    }
    drawPath(stem, white, style = Stroke(5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
}
