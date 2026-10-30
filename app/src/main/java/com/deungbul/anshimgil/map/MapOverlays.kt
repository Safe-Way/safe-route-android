package com.deungbul.anshimgil.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.deungbul.anshimgil.data.Facility
import com.deungbul.anshimgil.data.FacilityKind
import com.deungbul.anshimgil.data.MapLayers
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
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
        val points = MapPoints.fromLatLng(zone.points.map { (lat, lon) -> LatLng.from(lat, lon) })
        shapeLayer?.addPolygon(
            PolygonOptions.from(points, PolygonStyles.from(zone.fillColor.toInt(), 2f * density, zone.strokeColor.toInt())),
        )
    }

    val styles = mutableMapOf<FacilityKind, LabelStyles>()
    layers.facilities.forEach { facility ->
        val style = styles.getOrPut(facility.kind) {
            LabelStyles.from(
                LabelStyle.from(pillBitmap(facility.kind.label, facility.kind.color.toInt(), density))
                    .setAnchorPoint(0.5f, 1f)
                    .setApplyDpScale(false),
            )
        }
        labelLayer?.addLabel(LabelOptions.from(facility.latLng).setStyles(style))
    }
}

private val Facility.latLng: LatLng get() = LatLng.from(lat, lon)

/** A white rounded tag with a colored border and bold text, like the design mockup. */
private fun pillBitmap(text: String, color: Int, density: Float): Bitmap {
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
