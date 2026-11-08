package com.deungbul.anshimgil.ui.common

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.geometry.Size
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

/** The key of the page while the app is still loading: the cover stays on. */
const val LOADING_PAGE = "loading"

private val RevealGreen = Color(0xFF1F6B42)
private val Light = Color(0xFFF7FAF6)

/**
 * How pages change (11/8, to be chosen by the team: each can be tried from 설정 → 화면 전환 효과).
 * All are a cover drawn over the page — the map is drawn in its own surface and cannot be faded or clipped
 * itself — and none takes a touch, so nothing waits for them.
 */
enum class PageTransition(val label: String, val about: String) {
    NONE("없음", "바로 바뀌어요"),
    FADE("살짝 밝게", "새 화면이 옅은 빛에서 또렷해져요"),
    SOFT_CIRCLE("작은 원", "누른 자리에서 옅게 퍼져요"),
    WIPE("위에서 걷기", "옅은 막이 아래로 걷혀요"),
    CLOVER("세잎 초록", "누른 자리에서 초록 원이 열리고, 앱을 켤 때 세잎 모양"),
}

/** The transition picked on this phone (a trial setting, kept in the app's own preferences). */
object PageTransitions {
    var current by mutableStateOf(PageTransition.FADE)
        private set

    fun load(context: Context) {
        val name = context.getSharedPreferences("app", Context.MODE_PRIVATE).getString("page_transition", null)
        current = PageTransition.entries.firstOrNull { it.name == name } ?: PageTransition.FADE
    }

    fun set(context: Context, t: PageTransition) {
        current = t
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString("page_transition", t.name).apply()
    }
}

/**
 * Draws [style]'s cover when [pageKey] changes, opening from [origin] (where the walker last tapped) for the
 * round ones. While the app is loading the cover is on; it opens on the first page.
 */
@Composable
fun PageReveal(pageKey: String, origin: Offset?, style: PageTransition, modifier: Modifier = Modifier) {
    val open = remember { Animatable(if (pageKey == LOADING_PAGE && style != PageTransition.NONE) 0f else 1f) }
    var previous by remember { mutableStateOf<String?>(null) }
    var launch by remember { mutableStateOf(pageKey == LOADING_PAGE) }
    LaunchedEffect(pageKey) {
        val before = previous
        previous = pageKey
        if (style == PageTransition.NONE) {
            open.snapTo(1f)
            return@LaunchedEffect
        }
        if (pageKey == LOADING_PAGE) {
            open.snapTo(0f)
            return@LaunchedEffect
        }
        if (before == null) return@LaunchedEffect
        launch = before == LOADING_PAGE
        open.snapTo(0f)
        when (style) {
            PageTransition.CLOVER -> {
                if (launch) delay(280)
                open.animateTo(1f, tween(if (launch) 700 else 430, easing = FastOutSlowInEasing))
            }
            PageTransition.FADE -> open.animateTo(1f, tween(if (launch) 420 else 260, easing = LinearOutSlowInEasing))
            PageTransition.SOFT_CIRCLE -> open.animateTo(1f, tween(if (launch) 450 else 320, easing = FastOutSlowInEasing))
            PageTransition.WIPE -> open.animateTo(1f, tween(if (launch) 450 else 330, easing = FastOutSlowInEasing))
            PageTransition.NONE -> Unit
        }
    }
    val v = open.value
    if (v >= 1f) return
    Canvas(modifier) {
        when (style) {
            PageTransition.FADE -> drawRect(Light.copy(alpha = 0.85f * (1f - v)))
            PageTransition.SOFT_CIRCLE -> drawIris(center(origin, launch), v, Light.copy(alpha = 0.95f), ring = false)
            PageTransition.WIPE -> {
                val top = size.height * v
                drawRect(Light.copy(alpha = 0.95f * (1f - v * 0.4f)), Offset(0f, top), Size(size.width, size.height - top))
            }
            PageTransition.CLOVER -> {
                drawIris(center(origin, launch), v, RevealGreen, ring = true)
                if (launch) drawClover(Offset(size.width / 2, size.height / 2), alpha = (1f - v * 2.5f).coerceIn(0f, 1f), growth = 0.85f + 0.15f * (1f - v))
            }
            PageTransition.NONE -> Unit
        }
    }
}

private fun DrawScope.center(origin: Offset?, launch: Boolean) =
    if (launch || origin == null) Offset(size.width / 2, size.height / 2) else origin

/** A cover with a round hole growing from [at] to the farthest corner. */
private fun DrawScope.drawIris(at: Offset, v: Float, color: Color, ring: Boolean) {
    val farthest = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
        .maxOf { hypot(it.x - at.x, it.y - at.y) }
    val r = farthest * v
    val cover = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        if (r > 0f) addOval(Rect(at, r))
    }
    drawPath(cover, color)
    if (ring && r > 0f) drawCircle(Color.White.copy(alpha = 0.45f * (1f - v)), r, at, style = Stroke(4.dp.toPx()))
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
