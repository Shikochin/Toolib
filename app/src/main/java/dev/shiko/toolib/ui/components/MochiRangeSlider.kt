package dev.shiko.toolib.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import ui.feice.mochi.basic.MochiSliderDefaults
import ui.feice.mochi.theme.MochiTheme

/** Mochi-style dual-handle range slider for trimming media. */
@Composable
fun MochiRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentValue by rememberUpdatedState(value)
    val currentRange by rememberUpdatedState(valueRange)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val colors = MochiSliderDefaults.colors(
        activeTrack = MochiTheme.colorScheme.primary,
        inactiveTrack = MochiTheme.colorScheme.surfaceVariant,
        thumb = Color.White,
        thumbShadow = MochiTheme.colorScheme.onSurface.copy(alpha = 0.22f),
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val thumbWidth = with(density) { MochiSliderDefaults.ThumbWidth.toPx() }
                val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val travel = (size.width - thumbWidth).coerceAtLeast(1f)

                    fun xFor(fraction: Float): Float = thumbWidth / 2f + travel * fraction.coerceIn(0f, 1f)
                    fun fractionFor(x: Float): Float = ((x - thumbWidth / 2f) / travel).coerceIn(0f, 1f)

                    val range = currentValue
                    val left = xFor((range.start - currentRange.start) / span)
                    val right = xFor((range.endInclusive - currentRange.start) / span)
                    val handle = if (abs(down.position.x - left) <= abs(down.position.x - right)) 0 else 1

                    fun updateHandle(x: Float) {
                        val next = currentRange.start + fractionFor(x) * span
                        val current = currentValue
                        if (handle == 0) {
                            currentOnValueChange(next.coerceAtMost(current.endInclusive)..current.endInclusive)
                        } else {
                            currentOnValueChange(current.start..next.coerceAtLeast(current.start))
                        }
                    }

                    val gesture = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        if (abs(over.x) >= abs(over.y)) {
                            change.consume()
                            updateHandle(down.position.x + over.x)
                        }
                    }
                    if (gesture != null) {
                        drag(gesture.id) { change ->
                            updateHandle(change.position.x)
                            change.consume()
                        }
                    }
                }
            }
            .drawBehind {
                val alpha = if (enabled) 1f else 0.38f
                val thumbWidth = with(density) { MochiSliderDefaults.ThumbWidth.toPx() }
                val thumbHeight = with(density) { 24.dp.toPx() }
                val trackHeight = with(density) { 6.dp.toPx() }
                val travel = (size.width - thumbWidth).coerceAtLeast(1f)
                val leftEdge = thumbWidth / 2f
                val rightEdge = size.width - thumbWidth / 2f
                val centerY = size.height / 2f
                val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
                val startFraction = ((value.start - valueRange.start) / span).coerceIn(0f, 1f)
                val endFraction = ((value.endInclusive - valueRange.start) / span).coerceIn(0f, 1f)
                val startX = leftEdge + travel * startFraction
                val endX = leftEdge + travel * endFraction
                val trackRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                drawRoundRect(
                    color = colors.inactiveTrack,
                    topLeft = Offset(leftEdge, centerY - trackHeight / 2f),
                    size = Size(rightEdge - leftEdge, trackHeight),
                    cornerRadius = trackRadius,
                    alpha = alpha,
                )
                drawRoundRect(
                    color = colors.activeTrack,
                    topLeft = Offset(startX, centerY - trackHeight / 2f),
                    size = Size((endX - startX).coerceAtLeast(trackHeight), trackHeight),
                    cornerRadius = trackRadius,
                    alpha = alpha,
                )

                fun drawThumb(x: Float) {
                    val thumbRadius = CornerRadius(thumbHeight / 2f, thumbHeight / 2f)
                    drawRoundRect(
                        color = colors.thumbShadow,
                        topLeft = Offset(x - thumbWidth / 2f, centerY - thumbHeight / 2f + with(density) { 2.dp.toPx() }),
                        size = Size(thumbWidth, thumbHeight),
                        cornerRadius = thumbRadius,
                        alpha = alpha,
                    )
                    drawRoundRect(
                        color = colors.thumb,
                        topLeft = Offset(x - thumbWidth / 2f, centerY - thumbHeight / 2f),
                        size = Size(thumbWidth, thumbHeight),
                        cornerRadius = thumbRadius,
                        alpha = alpha,
                    )
                }
                drawThumb(startX)
                drawThumb(endX)
            },
    )
}
