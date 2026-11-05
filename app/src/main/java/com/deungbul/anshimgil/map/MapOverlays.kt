package com.deungbul.anshimgil.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.deungbul.anshimgil.data.Facility
import com.deungbul.anshimgil.data.FacilityKind
import com.deungbul.anshimgil.data.FacilityLook
import com.deungbul.anshimgil.data.HELP_KINDS
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.MapLayers
import com.deungbul.anshimgil.data.RouteResult
import com.deungbul.anshimgil.data.RouteType
import com.deungbul.anshimgil.data.distanceMeters
import com.deungbul.anshimgil.data.TurnMark
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.label.CompetitionType
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles

/** Replaces everything on the map with [layers]: zones underneath, facility labels on top. */
fun KakaoMap.showLayers(context: Context, layers: MapLayers) {
    val shapeLayer = shapeManager?.layer
    shapeLayer?.removeAll()
    labelManager?.layer?.removeAll()
    val labelLayer = labelManager?.layer

    val density = context.resources.displayMetrics.density
    layers.zones.forEach { zone ->
        // The SDK does not draw the edge from the last point back to the first, so close the ring.
        val ring = if (zone.points.first() == zone.points.last()) zone.points else zone.points + zone.points.first()
        val points = MapPoints.fromLatLng(ring.map { (lat, lon) -> LatLng.from(lat, lon) })
        shapeLayer?.addPolygon(
            PolygonOptions.from(points, PolygonStyles.from(zone.fillColor.toInt(), 2f * density, zone.strokeColor.toInt())),
        )
    }

    // One style per distinct tag, since text and look can differ within a kind.
    val styles = mutableMapOf<List<Any>, Pair<LabelStyles, Bitmap>>()
    fun styleOf(facility: Facility) = styles.getOrPut(listOf(facility.kind, facility.label, facility.look, facility.elevator)) {
        val bitmap = when (facility.look) {
            FacilityLook.NORMAL -> pillBitmap(facility.label, facility.kind.color.toInt(), density, elevator = facility.elevator)
            FacilityLook.GRAY -> pillBitmap(facility.label, GRAY, density)
            FacilityLook.DOT -> dotBitmap(GRAY, density, sizeDp = 10f)
        }
        LabelStyles.from(
            LabelStyle.from(bitmap)
                .setAnchorPoint(0.5f, if (facility.look == FacilityLook.DOT) 0.5f else 1f)
                .setApplyDpScale(false),
        ) to bitmap
    }

    // Too many tags in one place are unreadable, so the more important tag wins where tags would overlap
    // on screen ([tagRank]); zooming in makes room and shows the rest. The map redraws tags after each move.
    val placed = mutableListOf<android.graphics.Rect>()
    val gap = (2 * density).toInt()
    layers.facilities.sortedByDescending { tagRank(it) }.forEach { facility ->
        val (style, bitmap) = styleOf(facility)
        val p = runCatching { toScreenPoint(facility.latLng) }.getOrNull()
        if (p != null) {
            val top = if (facility.look == FacilityLook.DOT) p.y - bitmap.height / 2 else p.y - bitmap.height
            val box = android.graphics.Rect(p.x - bitmap.width / 2 - gap, top - gap, p.x + bitmap.width / 2 + gap, top + bitmap.height + gap)
            if (placed.any { android.graphics.Rect.intersects(it, box) }) return@forEach
            placed += box
        }
        // Places with more to say (e.g. a station's elevator exits) can be tapped; the tag carries the place.
        val options = LabelOptions.from(facility.latLng).setStyles(style).setRank(tagRank(facility))
        if (facility.detail != null) options.setClickable(true).setTag(facility)
        labelLayer?.addLabel(options)
    }
}

/**
 * Which tag wins when tags would overlap (higher shows): stations, then elevator exits, then places to
 * get help, then the rest. CCTV and grayed-out or unconfirmed places give way first.
 */
internal fun tagRank(facility: Facility): Long = when {
    facility.look != FacilityLook.NORMAL -> 0L
    facility.kind == FacilityKind.SUBWAY -> 50L
    facility.kind == FacilityKind.SUBWAY_ENTRANCE -> 40L
    facility.kind in HELP_KINDS -> 30L
    facility.kind == FacilityKind.CCTV -> 5L
    else -> 20L
}

/** Tags for places that are unavailable or unconfirmed. */
private val GRAY = 0xFF9AA39D.toInt()

private val Facility.latLng: LatLng get() = LatLng.from(lat, lon)

fun LatLon.toLatLng(): LatLng = LatLng.from(lat, lon)

/**
 * Draws both routes (the [selected] one on top and stronger), facility tags along them,
 * and start/end markers, then fits the camera to the routes.
 */
fun KakaoMap.showRoutes(context: Context, result: RouteResult, selected: RouteType, destinationName: String) {
    val density = context.resources.displayMetrics.density
    val lineLayer = routeLineManager?.layer ?: return
    lineLayer.removeAll()

    // Draw the unselected route first so the selected one sits on top.
    result.routes.sortedBy { it.type == selected }.forEach { route ->
        val isSelected = route.type == selected
        val color = when (route.type) {
            RouteType.SAFE -> if (isSelected) 0xFF1F6B42 else 0xFF86A893
            RouteType.SHORTEST -> if (isSelected) 0xFF17211B else 0xFF8A968E
        }.toInt()
        val width = (if (isSelected) 8f else 5f) * density
        val style = RouteLineStyle.from(width, color, 1.5f * density, 0xFFFFFFFF.toInt())
        lineLayer.addRouteLine(
            RouteLineOptions.from(RouteLineSegment.from(route.path.map { it.toLatLng() }, style)),
        )
    }

    // Fit the camera first: the tags are thinned out by where they fall on screen in the final view.
    val points = result.routes.flatMap { it.path }.map { it.toLatLng() }.toTypedArray()
    if (points.isNotEmpty()) moveCamera(CameraUpdateFactory.fitMapPoints(points, (48 * density).toInt()))
    showLayers(context, MapLayers(result.facilitiesOn(selected), zones = emptyList()))

    val path = result.route(selected)?.path ?: result.routes.firstOrNull()?.path ?: return
    labelManager?.layer?.let { labels ->
        labels.addLabel(
            LabelOptions.from(path.first().toLatLng())
                .setStyles(LabelStyles.from(LabelStyle.from(dotBitmap(0xFF2F6FDB.toInt(), density)).setApplyDpScale(false))),
        )
        labels.addLabel(
            LabelOptions.from(path.last().toLatLng())
                .setStyles(
                    LabelStyles.from(
                        LabelStyle.from(pillBitmap(destinationName.take(12), 0xFFB42318.toInt(), density))
                            .setAnchorPoint(0.5f, 1f)
                            .setApplyDpScale(false),
                    ),
                ),
        )
    }
}

/**
 * When there is no route (start and end too close or too far): the start as a blue dot and each place
 * as a tag, with the camera on [focus] (the start and the places worth walking to). Clears any old route.
 */
fun KakaoMap.showPoints(context: Context, start: LatLon, places: List<Pair<LatLon, String>>, focus: List<LatLon>) {
    showLayers(context, MapLayers(emptyList(), zones = emptyList()))
    routeLineManager?.layer?.removeAll()
    val density = context.resources.displayMetrics.density
    addMyLocation(context, start)
    places.forEachIndexed { i, (point, name) ->
        // The first place is the one to go to; others (the far destination) are drawn gray.
        val color = if (i == 0) 0xFFB42318.toInt() else GRAY
        labelManager?.layer?.addLabel(
            LabelOptions.from(point.toLatLng())
                .setStyles(LabelStyles.from(LabelStyle.from(pillBitmap(name.take(12), color, density)).setAnchorPoint(0.5f, 1f).setApplyDpScale(false)))
                .setRank(if (i == 0) 8L else 6L),
        )
    }
    val points = (listOf(start) + focus).map { it.toLatLng() }
    val spread = points.maxOf { a -> points.maxOf { b -> distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) } }
    if (spread < 150) {
        // fitMapPoints would zoom in all the way on two points a few meters apart.
        val mid = LatLng.from(points.map { it.latitude }.average(), points.map { it.longitude }.average())
        moveCamera(CameraUpdateFactory.newCenterPosition(mid, 18))
    } else {
        moveCamera(CameraUpdateFactory.fitMapPoints(points.toTypedArray(), (64 * density).toInt()))
    }
}

/**
 * The phone's position as a navigation arrow for the way the walker faces, above everything else.
 * Move it with `Label.moveTo` and turn it with [pointTo].
 */
fun KakaoMap.addMyLocation(context: Context, point: LatLon, sizeDp: Float = 22f): Label? {
    val density = context.resources.displayMetrics.density
    return labelManager?.layer?.addLabel(
        LabelOptions.from(point.toLatLng())
            .setStyles(
                LabelStyles.from(
                    // Centered so the arrow turns around the dot.
                    LabelStyle.from(headingBitmap(0xFF2F6FDB.toInt(), density, sizeDp)).setAnchorPoint(0.5f, 0.5f).setApplyDpScale(false),
                ),
            )
            .setRank(10),
    )
}

/**
 * A small round sign at each sharp turn with a ↰ or ↱ in it, the same symbols as the instruction bar,
 * so the walker sees where the next turn is and which way it goes. It always faces the screen.
 */
fun KakaoMap.addTurnArrows(context: Context, marks: List<TurnMark>) {
    val density = context.resources.displayMetrics.density
    val left = LabelStyles.from(LabelStyle.from(turnSignBitmap(density, left = true)).setAnchorPoint(0.5f, 0.5f).setApplyDpScale(false))
    val right = LabelStyles.from(LabelStyle.from(turnSignBitmap(density, left = false)).setAnchorPoint(0.5f, 0.5f).setApplyDpScale(false))
    // Their own layer with no competition: on the default layer the signs were hidden and shown again
    // whenever they met the map's own place names while the map moved, so they seemed to jump about.
    val layer = labelManager?.let {
        it.getLayer(TURN_SIGN_LAYER) ?: it.addLayer(
            LabelLayerOptions.from(TURN_SIGN_LAYER).setCompetitionType(CompetitionType.None).setZOrder(10_000),
        )
    }
    layer?.removeAll()
    marks.forEach { mark ->
        layer?.addLabel(LabelOptions.from(mark.point.toLatLng()).setStyles(if (mark.left) left else right))
    }
}

private const val TURN_SIGN_LAYER = "turnSigns"

/** A white disc with a dark green ring and a turn arrow (↰ for left, ↱ for right). */
private fun turnSignBitmap(density: Float, left: Boolean): Bitmap {
    val size = (26 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val s = size.toFloat()
    val c = s / 2
    canvas.drawCircle(c, c, c - density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    canvas.drawCircle(
        c, c, c - 1.75f * density,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TURN_INK; style = Paint.Style.STROKE; strokeWidth = 1.5f * density },
    )
    // Drawn as ↱ (right turn) and mirrored for left: a stem up from the bottom, then across to an arrowhead.
    canvas.save()
    if (left) canvas.scale(-1f, 1f, c, c)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TURN_INK; style = Paint.Style.STROKE; strokeWidth = 2.6f * density
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    val path = android.graphics.Path().apply {
        moveTo(s * 0.40f, s * 0.74f)
        lineTo(s * 0.40f, s * 0.48f)
        quadTo(s * 0.40f, s * 0.40f, s * 0.48f, s * 0.40f)
        lineTo(s * 0.62f, s * 0.40f)
    }
    canvas.drawPath(path, stroke)
    val head = android.graphics.Path().apply {
        moveTo(s * 0.74f, s * 0.40f)
        lineTo(s * 0.58f, s * 0.29f)
        lineTo(s * 0.58f, s * 0.51f)
        close()
    }
    canvas.drawPath(head, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TURN_INK })
    canvas.restore()
    return bitmap
}

private val TURN_INK = 0xFF1F6B42.toInt()

/** A small zebra-crossing sign at each crosswalk on the route. */
fun KakaoMap.addCrosswalks(context: Context, points: List<LatLon>) {
    val density = context.resources.displayMetrics.density
    val style = LabelStyles.from(LabelStyle.from(crosswalkBitmap(density)).setAnchorPoint(0.5f, 0.5f).setApplyDpScale(false))
    points.forEach { labelManager?.layer?.addLabel(LabelOptions.from(it.toLatLng()).setStyles(style).setRank(6)) }
}

/** A rounded white square with dark stripes, like a road crossing seen from above. */
private fun crosswalkBitmap(density: Float): Bitmap {
    val size = (22 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val s = size.toFloat()
    val r = 5 * density
    canvas.drawRoundRect(RectF(0f, 0f, s, s), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    canvas.drawRoundRect(
        RectF(density, density, s - density, s - density), r, r,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4540.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.5f * density },
    )
    val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4540.toInt() }
    val w = s / 9
    for (i in 0 until 4) {
        val x = w * (1 + 2 * i)
        canvas.drawRect(x, s * 0.25f, x + w, s * 0.75f, stripe)
    }
    return bitmap
}

/** Turns the position marker so its arrow points [degrees] clockwise from north, over [durationMs] if given. */
fun Label.pointTo(degrees: Float, durationMs: Int = 0) {
    val radians = Math.toRadians(degrees.toDouble()).toFloat()
    if (durationMs > 0) rotateTo(radians, durationMs) else rotateTo(radians)
}

/** Map tilt for walking screens: buildings stand up in 3D and more of the street ahead is visible. */
const val WALK_TILT_DEGREES = 50.0

fun KakaoMap.tiltForWalking() = moveCamera(CameraUpdateFactory.tiltTo(Math.toRadians(WALK_TILT_DEGREES)))

/**
 * The walker as a navigation arrow, like Naver's: a white disc with a blue ring and a blue arrow in it,
 * pointing up (north) until turned. Square, with the arrow in the middle, so turning keeps it in place.
 */
private fun headingBitmap(color: Int, density: Float, dotDp: Float): Bitmap {
    val d = dotDp * 1.5f * density
    val size = d.toInt() + 2
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val r = d / 2 - density
    canvas.drawCircle(c, c, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
    canvas.drawCircle(
        c, c, r - 1.25f * density,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 2.5f * density },
    )
    val a = r * 0.62f
    val arrow = android.graphics.Path().apply {
        moveTo(c, c - a)                 // tip
        lineTo(c + a * 0.72f, c + a * 0.75f)
        lineTo(c, c + a * 0.38f)         // notch at the back
        lineTo(c - a * 0.72f, c + a * 0.75f)
        close()
    }
    canvas.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    return bitmap
}

/** A filled circle with a white ring, used for the user's position. */
fun dotBitmap(color: Int, density: Float, sizeDp: Float = 18f): Bitmap {
    val size = (sizeDp * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val r = size / 2f
    canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
    canvas.drawCircle(r, r, r - 3f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    return bitmap
}

/**
 * A white rounded tag with a colored border and bold text, like the design mockup.
 * With [elevator], a small elevator sign follows the text (a station or exit with an elevator).
 */
internal fun pillBitmap(text: String, color: Int, density: Float, elevator: Boolean = false): Bitmap {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = 13f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    val stroke = 2f * density
    val padX = 10f * density
    val height = 30f * density
    val iconSize = 18f * density
    val iconGap = 6f * density
    val width = textPaint.measureText(text) + padX * 2 + if (elevator) iconSize + iconGap else 0f

    val bitmap = Bitmap.createBitmap(width.toInt() + 1, height.toInt() + 1, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val rect = RectF(stroke / 2, stroke / 2, width - stroke / 2, height - stroke / 2)
    val radius = height / 2
    canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
    canvas.drawRoundRect(
        rect, radius, radius,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = stroke
        },
    )
    val baseline = height / 2 - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(text, padX, baseline, textPaint)
    if (elevator) {
        // Elevator sign: a filled rounded square with an up and a down triangle.
        val left = width - padX - iconSize
        val top = (height - iconSize) / 2
        val box = RectF(left, top, left + iconSize, top + iconSize)
        canvas.drawRoundRect(box, 3f * density, 3f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
        val cx = box.centerX()
        val w = iconSize * 0.26f
        canvas.drawPath(android.graphics.Path().apply {
            moveTo(cx, top + iconSize * 0.14f); lineTo(cx + w, top + iconSize * 0.42f); lineTo(cx - w, top + iconSize * 0.42f); close()
        }, white)
        canvas.drawPath(android.graphics.Path().apply {
            moveTo(cx, top + iconSize * 0.86f); lineTo(cx + w, top + iconSize * 0.58f); lineTo(cx - w, top + iconSize * 0.58f); close()
        }, white)
    }
    return bitmap
}
