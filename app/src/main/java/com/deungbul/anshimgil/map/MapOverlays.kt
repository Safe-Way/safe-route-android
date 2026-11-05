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
import com.deungbul.anshimgil.data.LatLon
import com.deungbul.anshimgil.data.MapLayers
import com.deungbul.anshimgil.data.RouteResult
import com.deungbul.anshimgil.data.RouteType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles

/** Replaces everything on the map with [layers]: zones underneath, facility labels on top. */
fun KakaoMap.showLayers(context: Context, layers: MapLayers) {
    val shapeLayer = shapeManager?.layer
    val labelLayer = labelManager?.layer
    shapeLayer?.removeAll()
    labelLayer?.removeAll()

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
    val styles = mutableMapOf<Triple<FacilityKind, String, FacilityLook>, LabelStyles>()
    layers.facilities.forEach { facility ->
        val style = styles.getOrPut(Triple(facility.kind, facility.label, facility.look)) {
            val bitmap = when (facility.look) {
                FacilityLook.NORMAL -> pillBitmap(facility.label, facility.kind.color.toInt(), density)
                FacilityLook.GRAY -> pillBitmap(facility.label, GRAY, density)
                FacilityLook.DOT -> dotBitmap(GRAY, density, sizeDp = 10f)
            }
            LabelStyles.from(
                LabelStyle.from(bitmap)
                    .setAnchorPoint(0.5f, if (facility.look == FacilityLook.DOT) 0.5f else 1f)
                    .setApplyDpScale(false),
            )
        }
        // Stations sit on top of the toilets and stores inside them.
        val rank = if (facility.kind == FacilityKind.SUBWAY) 5L else 0L
        labelLayer?.addLabel(LabelOptions.from(facility.latLng).setStyles(style).setRank(rank))
    }
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
    showLayers(context, MapLayers(result.facilitiesOn(selected), zones = emptyList()))
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

    val points = result.routes.flatMap { it.path }.map { it.toLatLng() }.toTypedArray()
    if (points.isNotEmpty()) moveCamera(CameraUpdateFactory.fitMapPoints(points, (48 * density).toInt()))
}

/** The phone's position as a blue dot above everything else; move it later with `Label.moveTo`. */
fun KakaoMap.addMyLocation(context: Context, point: LatLon): Label? {
    val density = context.resources.displayMetrics.density
    return labelManager?.layer?.addLabel(
        LabelOptions.from(point.toLatLng())
            .setStyles(LabelStyles.from(LabelStyle.from(dotBitmap(0xFF2F6FDB.toInt(), density, 22f)).setApplyDpScale(false)))
            .setRank(10),
    )
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

/** A white rounded tag with a colored border and bold text, like the design mockup. */
internal fun pillBitmap(text: String, color: Int, density: Float): Bitmap {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = 13f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    val stroke = 2f * density
    val padX = 10f * density
    val height = 30f * density
    val width = textPaint.measureText(text) + padX * 2

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
    return bitmap
}
