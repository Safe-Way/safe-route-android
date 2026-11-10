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
import com.deungbul.anshimgil.data.schoolZoneCircle
import com.deungbul.anshimgil.data.TurnMark
import com.deungbul.anshimgil.data.TransitRoute
import com.deungbul.anshimgil.data.TransitLeg
import com.deungbul.anshimgil.data.LegType
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
import com.kakao.vectormap.label.TransformMethod
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
    // A dot or a bare picture sits on the place itself; a tag stands above it.
    fun centered(facility: Facility) = facility.look == FacilityLook.DOT ||
        (facility.look == FacilityLook.NORMAL && tagWords(facility).isEmpty() && !facility.elevator)
    fun styleOf(facility: Facility) = styles.getOrPut(listOf(facility.kind, facility.label, facility.look, facility.elevator)) {
        val bitmap = when (facility.look) {
            // Every kind as a picture instead of a word (11/8); what only words can say (an exit number, no
            // elevator) follows the picture in a tag.
            FacilityLook.NORMAL -> tagWords(facility).let { words ->
                if (centered(facility)) pictureBitmap(facility.kind, facility.kind.color.toInt(), density)
                else pillBitmap(words, facility.kind.color.toInt(), density, elevator = facility.elevator, picture = facility.kind)
            }
            FacilityLook.GRAY -> pillBitmap(tagWords(facility), GRAY, density, picture = facility.kind)
            FacilityLook.DOT -> dotBitmap(GRAY, density, sizeDp = 10f)
        }
        LabelStyles.from(
            LabelStyle.from(bitmap)
                .setAnchorPoint(0.5f, if (centered(facility)) 0.5f else 1f)
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
            val top = if (centered(facility)) p.y - bitmap.height / 2 else p.y - bitmap.height
            val box = android.graphics.Rect(p.x - bitmap.width / 2 - gap, top - gap, p.x + bitmap.width / 2 + gap, top + bitmap.height + gap)
            if (placed.any { android.graphics.Rect.intersects(it, box) }) return@forEach
            placed += box
        }
        // Every place can be tapped to tell what its picture means (11/9), and more where the server says more
        // (e.g. a station's elevator exits); the tag carries the place.
        val options = LabelOptions.from(facility.latLng).setStyles(style).setRank(tagRank(facility))
            .setClickable(true).setTag(facility)
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
fun KakaoMap.showRoutes(
    context: Context,
    result: RouteResult,
    selected: RouteType,
    destinationName: String,
    /** Walking guidance: a darker, wider line with a wider white edge, still clear in sunlight (사용성 16번). */
    walking: Boolean = false,
) {
    val density = context.resources.displayMetrics.density
    val lineLayer = routeLineManager?.layer ?: return
    lineLayer.removeAll()

    // Draw the unselected route first so the selected one sits on top.
    result.routes.sortedBy { it.type == selected }.forEach { route ->
        val isSelected = route.type == selected
        val color = when (route.type) {
            RouteType.SAFE -> if (walking) 0xFF0B3D23 else if (isSelected) 0xFF1F6B42 else 0xFF86A893
            RouteType.SHORTEST -> if (isSelected) 0xFF17211B else 0xFF8A968E
        }.toInt()
        val width = (if (walking) 11f else if (isSelected) 8f else 5f) * density
        val style = RouteLineStyle.from(width, color, (if (walking) 2.5f else 1.5f) * density, 0xFFFFFFFF.toInt())
        lineLayer.addRouteLine(
            RouteLineOptions.from(RouteLineSegment.from(route.path.map { it.toLatLng() }, style)),
        )
    }

    // Fit the camera first: the tags are thinned out by where they fall on screen in the final view.
    val points = result.routes.flatMap { it.path }.map { it.toLatLng() }.toTypedArray()
    if (points.isNotEmpty()) moveCamera(CameraUpdateFactory.fitMapPoints(points, (48 * density).toInt()))
    val shown = result.facilitiesOn(selected)
    val zones = shown.filter { it.kind == FacilityKind.SCHOOL_ZONE }.map { schoolZoneCircle(it.lat, it.lon) }
    showLayers(context, MapLayers(shown, zones = zones))

    val path = result.route(selected)?.path ?: result.routes.firstOrNull()?.path ?: return
    addStopPins(context, (result.route(selected) ?: result.routes.first()).stops.map { it.point })
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
    // A layer of its own drawn above every tag, so a nearby store or CCTV tag never covers the walker.
    // One walker per map, so whatever was there before is replaced.
    val layer = labelManager?.let {
        it.getLayer(ME_LAYER) ?: it.addLayer(LabelLayerOptions.from(ME_LAYER).setCompetitionType(CompetitionType.None).setZOrder(20_000))
    }
    layer?.removeAll()
    return layer?.addLabel(
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
    // Fixed to the map rather than the screen, turned the way the walker comes in, so as the map turns with
    // the walker each sign keeps pointing along its own street instead of spinning.
    marks.forEach { mark ->
        layer?.addLabel(
            LabelOptions.from(mark.point.toLatLng())
                .setStyles(if (mark.left) left else right)
                .setTransform(TransformMethod.AbsoluteRotation),
        )?.rotateTo(Math.toRadians(mark.bearing.toDouble()).toFloat())
    }
}

private const val TURN_SIGN_LAYER = "turnSigns"
private const val ME_LAYER = "me"

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

/** 들를 곳: a numbered orange pin (1, 2, 3) at each place to stop at, in order. */
fun KakaoMap.addStopPins(context: Context, points: List<LatLon>) =
    addStopPins(context, points.mapIndexed { i, p -> i + 1 to p })

/** Numbered pins with the given numbers (stops already done or skipped are left out but keep their numbers). */
@JvmName("addNumberedStopPins")
fun KakaoMap.addStopPins(context: Context, pins: List<Pair<Int, LatLon>>) {
    val density = context.resources.displayMetrics.density
    pins.forEach { (n, p) ->
        labelManager?.layer?.addLabel(
            LabelOptions.from(p.toLatLng())
                .setStyles(LabelStyles.from(LabelStyle.from(stopPinBitmap(n, density)).setAnchorPoint(0.5f, 1f).setApplyDpScale(false)))
                .setRank(30),
        )
    }
}

/** A round orange pin with a white number and a point at the bottom. */
internal fun stopPinBitmap(number: Int, density: Float): Bitmap {
    val w = (30 * density).toInt()
    val h = (38 * density).toInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val r = w / 2f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB45309.toInt() }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    val tip = android.graphics.Path().apply {
        moveTo(r - 7 * density, r * 1.5f); lineTo(r, h.toFloat()); lineTo(r + 7 * density, r * 1.5f); close()
    }
    canvas.drawPath(tip, fill)
    canvas.drawCircle(r, r, r, ring)
    canvas.drawCircle(r, r, r - 2.5f * density, fill)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = 17 * density; textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText(number.toString(), r, r - (text.descent() + text.ascent()) / 2, text)
    return bitmap
}

/** A ride's line: its stops in order, or just where to get on and off. */
internal fun ridePoints(leg: TransitLeg): List<LatLon> =
    leg.stops.map { it.point }.takeIf { it.size >= 2 } ?: listOf(leg.from.point, leg.to.point)

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

/** The words a tag still needs next to its picture: its own text without the kind's name ("지하철역 · …"). */
internal fun tagWords(facility: Facility): String =
    (facility.text ?: "").removePrefix(facility.kind.label).trim().removePrefix("·").trim()

/**
 * The kind's picture ([drawPicture]) on its own, in its color: no round tag behind it (11/9, the user's call:
 * the map reads more cleanly without the white circles). Only the walker's own position keeps its disc.
 */
internal fun pictureBitmap(kind: FacilityKind, color: Int, density: Float): Bitmap {
    val size = PICTURE_DP * density
    val bitmap = Bitmap.createBitmap(size.toInt() + 1, size.toInt() + 1, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(size / 24f, size / 24f)
    drawPicture(canvas, kind, color)
    return bitmap
}

/** How big a bare picture is on the map: a little bigger than inside the old 30 dp tag (18 dp), to read alone. */
private const val PICTURE_DP = 24f

/**
 * Draws [kind]'s picture in [color] on a 24-unit grid at the canvas's origin (scale the canvas first):
 * the same pictures as the home screen's buttons where there is one, simple signs elsewhere.
 */
internal fun drawPicture(canvas: Canvas, kind: FacilityKind, color: Int) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
    fun line(width: Float = 2.2f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    fun path(block: android.graphics.Path.() -> Unit) = android.graphics.Path().apply(block)
    fun star(cx: Float, cy: Float, r: Float) = path {
        for (i in 0 until 10) {
            val a = Math.toRadians(-90.0 + i * 36.0)
            val rr = if (i % 2 == 0) r else r * 0.45f
            val x = cx + (rr * kotlin.math.cos(a)).toFloat()
            val y = cy + (rr * kotlin.math.sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    // Bold enough to read on the map without a tag behind it (11/9).
    fun person(cx: Float, paint: Paint, dress: Boolean) {
        canvas.drawCircle(cx, 4f, 2.7f, paint)
        if (dress) {
            canvas.drawPath(path { moveTo(cx - 1.6f, 7.5f); lineTo(cx + 1.6f, 7.5f); lineTo(cx + 4.8f, 16f); lineTo(cx - 4.8f, 16f); close() }, paint)
        } else {
            canvas.drawRoundRect(RectF(cx - 3.4f, 7.5f, cx + 3.4f, 16f), 1.6f, 1.6f, paint)
        }
        val leg = line(2.6f).apply { this.color = paint.color; strokeCap = Paint.Cap.BUTT }
        canvas.drawLine(cx - 1.6f, 15.5f, cx - 1.6f, 22.5f, leg)
        canvas.drawLine(cx + 1.6f, 15.5f, cx + 1.6f, 22.5f, leg)
    }
    when (kind) {
        FacilityKind.CCTV -> {
            // A dome camera under its ceiling plate, the lens in the middle (11/9: the bullet camera on an arm read
            // as a tadpole on the map).
            canvas.drawRoundRect(RectF(2f, 6.5f, 22f, 9.5f), 1.2f, 1.2f, fill)
            canvas.drawArc(RectF(3.5f, 1f, 20.5f, 18f), 0f, 180f, true, fill)
            canvas.drawCircle(12f, 14f, 3f, white)
            canvas.drawCircle(12f, 14f, 1.4f, fill)
        }
        FacilityKind.CONVENIENCE_STORE -> {
            // Awning with stripes, the shop below it and its door.
            canvas.drawPath(path { moveTo(4f, 3.5f); lineTo(20f, 3.5f); lineTo(22f, 9f); lineTo(2f, 9f); close() }, fill)
            canvas.drawRect(RectF(7.6f, 3.5f, 9.2f, 9f), white)
            canvas.drawRect(RectF(14.8f, 3.5f, 16.4f, 9f), white)
            canvas.drawRect(RectF(4f, 10.5f, 20f, 21f), line())
            canvas.drawRect(RectF(9.5f, 14f, 14.5f, 21f), fill)
        }
        FacilityKind.POLICE -> {
            // A shield with a star, as on the map's 파출소 button.
            canvas.drawPath(path {
                moveTo(12f, 2f); lineTo(20f, 5f); lineTo(20f, 11f)
                cubicTo(20f, 16f, 16.5f, 20f, 12f, 22f); cubicTo(7.5f, 20f, 4f, 16f, 4f, 11f); lineTo(4f, 5f); close()
            }, fill)
            canvas.drawPath(star(12f, 11.5f, 4.6f), white)
        }
        FacilityKind.GUARD_HOUSE -> {
            // A house with a heart: a shop or home that keeps children safe.
            canvas.drawPath(path {
                moveTo(12f, 2.5f); lineTo(22.5f, 11.5f); lineTo(19.5f, 11.5f); lineTo(19.5f, 21f)
                lineTo(4.5f, 21f); lineTo(4.5f, 11.5f); lineTo(1.5f, 11.5f); close()
            }, fill)
            canvas.drawCircle(10.4f, 13.6f, 1.9f, white)
            canvas.drawCircle(13.6f, 13.6f, 1.9f, white)
            canvas.drawPath(path { moveTo(8.6f, 14.3f); lineTo(15.4f, 14.3f); lineTo(12f, 18.6f); close() }, white)
        }
        FacilityKind.EMERGENCY_BELL -> {
            // A bell.
            canvas.drawPath(path {
                moveTo(12f, 3f); cubicTo(8f, 3f, 6f, 6f, 6f, 10f); lineTo(6f, 15f); lineTo(3.5f, 18f)
                lineTo(20.5f, 18f); lineTo(18f, 15f); lineTo(18f, 10f); cubicTo(18f, 6f, 16f, 3f, 12f, 3f); close()
            }, fill)
            canvas.drawCircle(12f, 20.3f, 2f, fill)
            canvas.drawCircle(12f, 2.6f, 1.3f, fill)
        }
        FacilityKind.HEAT_SHELTER, FacilityKind.COLD_SHELTER -> {
            // A shelter: a house with the sun (무더위쉼터) or a snowflake (한파쉼터) inside it (11/9: the sun or
            // snowflake alone read as the weather).
            canvas.drawPath(path {
                moveTo(12f, 1.5f); lineTo(23f, 11f); lineTo(20f, 11f); lineTo(20f, 22f)
                lineTo(4f, 22f); lineTo(4f, 11f); lineTo(1f, 11f); close()
            }, fill)
            val cx = 12f
            val cy = 15.5f
            val mark = line(1.5f).apply { this.color = 0xFFFFFFFF.toInt() }
            if (kind == FacilityKind.HEAT_SHELTER) {
                canvas.drawCircle(cx, cy, 2.3f, white)
                for (i in 0 until 8) {
                    val t = Math.toRadians(i * 45.0)
                    val c = kotlin.math.cos(t).toFloat()
                    val sn = kotlin.math.sin(t).toFloat()
                    canvas.drawLine(cx + 3.4f * c, cy + 3.4f * sn, cx + 4.8f * c, cy + 4.8f * sn, mark)
                }
            } else {
                for (i in 0 until 3) {
                    val t = Math.toRadians(i * 60.0 + 90.0)
                    val c = kotlin.math.cos(t).toFloat()
                    val sn = kotlin.math.sin(t).toFloat()
                    canvas.drawLine(cx - 4.8f * c, cy - 4.8f * sn, cx + 4.8f * c, cy + 4.8f * sn, mark)
                }
            }
        }
        FacilityKind.TOILET -> {
            // A man and a woman, as on the map's 화장실 button.
            person(5.8f, fill, dress = false)
            person(18.2f, fill, dress = true)
            canvas.drawLine(12f, 2.5f, 12f, 22f, line(1.6f))
        }
        FacilityKind.ACCESSIBLE_TOILET -> {
            // A person in a wheelchair.
            canvas.drawCircle(10.5f, 3.8f, 2.1f, fill)
            val body = line(2.4f)
            canvas.drawPath(path { moveTo(10.5f, 7.5f); lineTo(10.5f, 13.5f); lineTo(16f, 13.5f); lineTo(18.5f, 19.5f) }, body)
            canvas.drawLine(10.5f, 10f, 15f, 10f, body)
            canvas.drawArc(RectF(4f, 10.5f, 15f, 21.5f), 20f, 250f, false, line(2f))
        }
        FacilityKind.CURB_RAMP -> {
            // Crossing stripes over a ramp.
            for (x in listOf(3.5f, 8f, 12.5f, 17f)) canvas.drawRect(RectF(x, 4f, x + 3f, 15f), fill)
            canvas.drawPath(path { moveTo(2f, 21f); lineTo(22f, 21f); lineTo(22f, 17.5f); close() }, fill)
        }
        FacilityKind.ELEVATOR -> {
            // An elevator sign: up and down.
            canvas.drawRoundRect(RectF(4f, 3f, 20f, 21f), 3f, 3f, fill)
            canvas.drawPath(path { moveTo(12f, 5.5f); lineTo(16f, 10.8f); lineTo(8f, 10.8f); close() }, white)
            canvas.drawPath(path { moveTo(12f, 18.5f); lineTo(16f, 13.2f); lineTo(8f, 13.2f); close() }, white)
        }
        FacilityKind.OVERPASS -> {
            // A bridge over the road: deck, railing and legs.
            canvas.drawRect(RectF(1.5f, 10f, 22.5f, 13f), fill)
            canvas.drawLine(2.5f, 6f, 21.5f, 6f, line(1.6f))
            for (x in listOf(5f, 10f, 14f, 19f)) canvas.drawLine(x, 6f, x, 10f, line(1.4f))
            canvas.drawRect(RectF(4.5f, 13f, 7f, 21f), fill)
            canvas.drawRect(RectF(17f, 13f, 19.5f, 21f), fill)
        }
        FacilityKind.STAIRS -> {
            // Steps seen from the side: the block's outline, filled pale (11/9: solid was too heavy, a line alone
            // read as a wave).
            val steps = path {
                moveTo(2.5f, 21f); lineTo(2.5f, 17f); lineTo(7.5f, 17f); lineTo(7.5f, 12.5f); lineTo(12.5f, 12.5f)
                lineTo(12.5f, 8f); lineTo(17.5f, 8f); lineTo(17.5f, 3.5f); lineTo(21.5f, 3.5f); lineTo(21.5f, 21f); close()
            }
            canvas.drawPath(steps, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (color and 0x00FFFFFF) or 0x40000000 })
            canvas.drawPath(steps, line(2f))
        }
        FacilityKind.SUBWAY -> {
            // The front of a train.
            canvas.drawRoundRect(RectF(5f, 2.5f, 19f, 17f), 3.5f, 3.5f, fill)
            canvas.drawRoundRect(RectF(7.3f, 5f, 16.7f, 10.5f), 1f, 1f, white)
            canvas.drawCircle(8.7f, 13.7f, 1.3f, white)
            canvas.drawCircle(15.3f, 13.7f, 1.3f, white)
            val leg = line(2f)
            canvas.drawLine(8f, 17.5f, 5.5f, 21.5f, leg)
            canvas.drawLine(16f, 17.5f, 18.5f, 21.5f, leg)
        }
        FacilityKind.SUBWAY_ENTRANCE -> {
            // Going down: an arrow into steps.
            canvas.drawPath(path {
                moveTo(10f, 2.5f); lineTo(14f, 2.5f); lineTo(14f, 8f); lineTo(17.5f, 8f); lineTo(12f, 13.5f)
                lineTo(6.5f, 8f); lineTo(10f, 8f); close()
            }, fill)
            canvas.drawPath(path {
                moveTo(2.5f, 15.5f); lineTo(8f, 15.5f); lineTo(8f, 18.2f); lineTo(14f, 18.2f); lineTo(14f, 21f)
                lineTo(21.5f, 21f)
            }, line(2f))
        }
        FacilityKind.SCHOOL_ZONE -> {
            // A child walking.
            canvas.drawCircle(12f, 4.5f, 2.6f, fill)
            canvas.drawRoundRect(RectF(9.2f, 8f, 14.8f, 15f), 2f, 2f, fill)
            val limb = line(2f)
            canvas.drawLine(10.8f, 15f, 9f, 21f, limb)
            canvas.drawLine(13.2f, 15f, 15f, 21f, limb)
            canvas.drawLine(9.2f, 9.5f, 6.5f, 13f, limb)
            canvas.drawLine(14.8f, 9.5f, 17.5f, 13f, limb)
        }
    }
}

/**
 * A white rounded tag with a colored border and bold text, like the design mockup.
 * With [elevator], a small elevator sign follows the text (a station or exit with an elevator).
 */
internal fun pillBitmap(text: String, color: Int, density: Float, elevator: Boolean = false, picture: FacilityKind? = null): Bitmap {
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
    // [picture] goes first, the words after it.
    val pictureSize = 20f * density
    val leading = if (picture != null) pictureSize + if (text.isNotEmpty()) iconGap else 0f else 0f
    val trailingGap = if (elevator && (text.isNotEmpty() || picture != null)) iconGap else 0f
    val width = leading + textPaint.measureText(text) + padX * 2 + if (elevator) iconSize + trailingGap else 0f

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
    picture?.let { kind ->
        canvas.save()
        canvas.translate(padX - 2f * density, (height - pictureSize) / 2)
        canvas.scale(pictureSize / 24f, pictureSize / 24f)
        drawPicture(canvas, kind, color)
        canvas.restore()
    }
    canvas.drawText(text, padX + leading, baseline, textPaint)
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

/**
 * A transit route on the map: walking parts along their (safer) walking line, rides as a straight
 * line from the stop where the walker gets on to where they get off, a tag at each boarding stop
 * ("45번", "1호선"), and the start and destination. Fits the camera to it.
 */
fun KakaoMap.showTransit(context: Context, route: TransitRoute, destinationName: String) {
    showLayers(context, MapLayers(emptyList(), zones = emptyList()))
    val density = context.resources.displayMetrics.density
    val lineLayer = routeLineManager?.layer ?: return
    lineLayer.removeAll()
    val all = mutableListOf<LatLon>()
    // 들를 곳: a numbered pin each, no line.
    addStopPins(context, route.legs.filter { it.type == LegType.STOP }.map { it.to.point }.also { all += it })
    route.legs.filter { it.type != LegType.STOP }.forEach { leg ->
        val (points, color) = when (leg.type) {
            LegType.STOP -> return@forEach
            LegType.WALK -> (leg.path.ifEmpty { listOf(leg.from.point, leg.to.point) }) to 0xFF1F6B42.toInt()
            // Through every stop on the way (#110) when known, so the line follows the bus, not a straight cut.
            LegType.BUS -> ridePoints(leg) to 0xFF2F5BB7.toInt()
            LegType.SUBWAY -> ridePoints(leg) to 0xFF6D28D9.toInt()
        }
        all += points
        val style = RouteLineStyle.from((if (leg.type == LegType.WALK) 6f else 7f) * density, color, 1.5f * density, 0xFFFFFFFF.toInt())
        lineLayer.addRouteLine(RouteLineOptions.from(RouteLineSegment.from(points.map { it.toLatLng() }, style)))
        if (leg.type != LegType.WALK) {
            val tag = if (leg.type == LegType.BUS) leg.lines.joinToString(",") { "${it}번" } else (leg.lines.firstOrNull() ?: "지하철")
            labelManager?.layer?.addLabel(
                LabelOptions.from(leg.from.point.toLatLng())
                    .setStyles(LabelStyles.from(LabelStyle.from(pillBitmap(tag.take(12), color, density)).setAnchorPoint(0.5f, 1f).setApplyDpScale(false)))
                    .setRank(20),
            )
        }
    }
    val start = route.legs.firstOrNull()?.from?.point ?: return
    val end = route.legs.lastOrNull()?.to?.point ?: return
    labelManager?.layer?.addLabel(
        LabelOptions.from(start.toLatLng()).setStyles(LabelStyles.from(LabelStyle.from(dotBitmap(0xFF2F6FDB.toInt(), density)).setApplyDpScale(false))),
    )
    labelManager?.layer?.addLabel(
        LabelOptions.from(end.toLatLng())
            .setStyles(LabelStyles.from(LabelStyle.from(pillBitmap(destinationName.take(12), 0xFFB42318.toInt(), density)).setAnchorPoint(0.5f, 1f).setApplyDpScale(false))),
    )
    if (all.isNotEmpty()) moveCamera(CameraUpdateFactory.fitMapPoints(all.map { it.toLatLng() }.toTypedArray(), (48 * density).toInt()))
}

private const val REPORT_LAYER = "reportSpots"

/**
 * 신고 쌓인 곳 (11/10): each spot as an orange tag ("공사 3") on a layer of its own above the facility tags, so it
 * is never hidden by them; tapping it gives the spot ([Label.getTag]). Replaces the spots drawn before.
 */
fun KakaoMap.showReportSpots(context: Context, spots: List<com.deungbul.anshimgil.data.ReportSpot>) {
    val density = context.resources.displayMetrics.density
    val layer = labelManager?.let {
        it.getLayer(REPORT_LAYER) ?: it.addLayer(LabelLayerOptions.from(REPORT_LAYER).setCompetitionType(CompetitionType.None).setZOrder(5_000))
    } ?: return
    layer.removeAll()
    spots.forEach { spot ->
        // Darker once confirmed (결정 57: 2 reports this period, or the team checked it).
        val color = if (spot.confirmed) 0xFF9A3412.toInt() else 0xFFD97706.toInt()
        // On a stem: the walker's arrow (about 33 dp across) stands on the spot itself, and the tag sat right under it
        // (11/10 phone check). The stem's foot marks the place; the tag stays readable above the arrow.
        val bitmap = stemmed(pillBitmap("⚠ " + com.deungbul.anshimgil.data.reportSpotTag(spot), color, density), color, density)
        layer.addLabel(
            LabelOptions.from(LatLng.from(spot.point.lat, spot.point.lon))
                .setStyles(LabelStyles.from(LabelStyle.from(bitmap).setAnchorPoint(0.5f, 1f).setApplyDpScale(false)))
                .setClickable(true)
                .setTag(spot),
        )
    }
}

/** Clear room between a tag and the place it stands for, so the walker's arrow there does not cover the tag. */
private const val TAG_STEM_DP = 24f

/** [tag] raised on a thin stem whose foot (a small dot, the bitmap's bottom middle) marks the place. */
private fun stemmed(tag: Bitmap, color: Int, density: Float): Bitmap {
    val stem = TAG_STEM_DP * density
    val bitmap = Bitmap.createBitmap(tag.width, tag.height + stem.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val x = tag.width / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 2f * density }
    canvas.drawLine(x, tag.height.toFloat(), x, bitmap.height - 3f * density, paint)
    canvas.drawCircle(x, bitmap.height - 3f * density, 3f * density, paint)
    canvas.drawBitmap(tag, 0f, 0f, null)
    return bitmap
}
