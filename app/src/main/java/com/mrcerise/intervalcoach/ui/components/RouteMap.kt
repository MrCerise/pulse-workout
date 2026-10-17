package com.mrcerise.intervalcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pulse.engine.RacePoint
import com.mrcerise.intervalcoach.ui.theme.LocalPulseColors

/** The route taken, drawn as a polyline fitted to its bounding box (no map tiles, works offline). */
@Composable
fun RouteMap(points: List<RacePoint>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 160.dp) {
    val colors = LocalPulseColors.current
    if (points.size < 2) return
    Canvas(modifier.fillMaxWidth().height(height)) {
        val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }; val maxLng = points.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).takeIf { it > 0 } ?: 1.0
        val lngSpan = (maxLng - minLng).takeIf { it > 0 } ?: 1.0
        val pad = 24f
        fun x(lng: Double) = (pad + (lng - minLng) / lngSpan * (size.width - 2 * pad)).toFloat()
        fun y(lat: Double) = (size.height - pad - (lat - minLat) / latSpan * (size.height - 2 * pad)).toFloat()
        val path = Path().apply {
            moveTo(x(points.first().longitude), y(points.first().latitude))
            points.drop(1).forEach { lineTo(x(it.longitude), y(it.latitude)) }
        }
        drawPath(path, color = colors.accent, style = Stroke(width = 6f))
        drawCircle(colors.success, radius = 10f, center = Offset(x(points.first().longitude), y(points.first().latitude)))
        drawCircle(colors.error, radius = 10f, center = Offset(x(points.last().longitude), y(points.last().latitude)))
    }
}
