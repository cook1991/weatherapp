package com.spritzlive.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val START_ANGLE = 135f   // degrees, 0 = 3 o'clock, CW positive
private const val SWEEP_ANGLE = 270f

/**
 * A dial echoing the speedometer's gauge, repurposed for temperature. The arc
 * spans today's low → high; the filled portion marks where the current temp sits
 * within that range, with a cool→warm gradient. Low/high labels sit at the ends.
 */
@Composable
fun TempDial(
    current: Float,
    low: Float,
    high: Float,
    degree: String,
    cool: Color,
    warm: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val span = (high - low).coerceAtLeast(1f)
    val fraction = ((current - low) / span).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = fraction, label = "tempFill")
    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(16.dp)
        ) {
            drawDial(animated, low, high, degree, cool, warm, trackColor, textMeasurer)
        }
    }
}

private fun DrawScope.drawDial(
    fraction: Float,
    low: Float,
    high: Float,
    degree: String,
    cool: Color,
    warm: Color,
    trackColor: Color,
    textMeasurer: TextMeasurer,
) {
    val stroke = size.minDimension * 0.07f
    val radius = (minOf(size.width, size.height) - stroke) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)

    // Background track.
    drawArc(
        color = trackColor,
        startAngle = START_ANGLE,
        sweepAngle = SWEEP_ANGLE,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )

    // Filled arc from the low end up to the current temperature.
    drawArc(
        brush = Brush.sweepGradient(listOf(cool, cool, warm, warm), center),
        startAngle = START_ANGLE,
        sweepAngle = SWEEP_ANGLE * fraction,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )

    // Low / high labels at the two ends of the arc.
    drawEndLabel(textMeasurer, "${low.roundToInt()}$degree", START_ANGLE, radius, center, stroke)
    drawEndLabel(textMeasurer, "${high.roundToInt()}$degree", START_ANGLE + SWEEP_ANGLE, radius, center, stroke)
}

private fun DrawScope.drawEndLabel(
    textMeasurer: TextMeasurer,
    text: String,
    angleDeg: Float,
    radius: Float,
    center: Offset,
    stroke: Float,
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val labelRadius = radius - stroke * 2.2f
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    val measured = textMeasurer.measure(text)
    val x = center.x + cos * labelRadius - measured.size.width / 2f
    val y = center.y + sin * labelRadius - measured.size.height / 2f
    drawText(measured, color = Muted, topLeft = Offset(x, y))
}
