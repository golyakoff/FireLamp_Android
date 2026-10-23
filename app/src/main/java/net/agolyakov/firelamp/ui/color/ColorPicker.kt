package net.agolyakov.firelamp.ui.color

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** `0xRRGGBB` to a Compose color. */
fun rgbColor(rgb: Int): Color = Color(0xFF000000.toInt() or rgb)

/** The lamp's color of a FastLED hue and saturation `0..255`. */
fun lampHueColor(hue: Int, saturation: Int = 255): Color = rgbColor(FastLedColor.hsvToRgb(hue, saturation))

/**
 * Picks red, green, blue `0..255` (the "Color lamp" effect): a hue/saturation wheel, a
 * brightness slider and a few presets. Every change goes to [onChange] at once, so the lamp shows
 * it live; Cancel puts the initial color back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RgbColorDialog(
    title: String,
    red: Int,
    green: Int,
    blue: Int,
    onChange: (red: Int, green: Int, blue: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember { Triple(red, green, blue) }
    val start = remember {
        FloatArray(3).also { android.graphics.Color.RGBToHSV(red, green, blue, it) }
    }
    var hue by remember { mutableFloatStateOf(start[0] / 360f) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }

    fun emit() {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, saturation, value))
        onChange(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
    }

    PickerDialog(
        title = title,
        onDone = onDismiss,
        onCancel = {
            onChange(initial.first, initial.second, initial.third)
            onDismiss()
        }
    ) {
        ColorWheel(
            hue = hue,
            saturation = saturation,
            colorAt = { h, s -> Color.hsv(h * 360f, s, 1f) },
            dim = 1f - value,
            onChange = { h, s ->
                hue = h
                saturation = s
                emit()
            }
        )
        Text(stringResource(R.string.color_brightness), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value,
            onValueChange = {
                value = it
                emit()
            },
            valueRange = 0.05f..1f
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { rgb ->
                Swatch(rgbColor(rgb)) {
                    val hsv = FloatArray(3)
                    android.graphics.Color.RGBToHSV(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF, hsv)
                    hue = hsv[0] / 360f
                    saturation = hsv[1]
                    value = hsv[2]
                    emit()
                }
            }
        }
    }
}

/**
 * Picks a FastLED hue and saturation `0..255` on a wheel drawn with the lamp's own rainbow.
 * Live like [RgbColorDialog].
 */
@Composable
fun HueSaturationDialog(
    title: String,
    hue: Int,
    saturation: Int,
    onChange: (hue: Int, saturation: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember { hue to saturation }
    var h by remember { mutableFloatStateOf(hue / 256f) }
    var s by remember { mutableFloatStateOf(saturation / 255f) }

    PickerDialog(
        title = title,
        onDone = onDismiss,
        onCancel = {
            onChange(initial.first, initial.second)
            onDismiss()
        }
    ) {
        ColorWheel(
            hue = h,
            saturation = s,
            colorAt = { wh, ws -> lampHueColor((wh * 256).roundToInt() and 0xFF, (ws * 255).roundToInt()) },
            onChange = { wh, ws ->
                h = wh
                s = ws
                onChange((wh * 256).roundToInt() and 0xFF, (ws * 255).roundToInt())
            }
        )
    }
}

/**
 * Picks a FastLED hue `0..255` alone (effects without a saturation): the rim of the wheel, a ring
 * of the lamp's rainbow. Live like [RgbColorDialog].
 */
@Composable
fun HueDialog(
    title: String,
    hue: Int,
    onChange: (hue: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember { hue }
    var h by remember { mutableFloatStateOf(hue / 256f) }

    PickerDialog(
        title = title,
        onDone = onDismiss,
        onCancel = {
            onChange(initial)
            onDismiss()
        }
    ) {
        ColorWheel(
            hue = h,
            saturation = 1f,
            colorAt = { wh, ws -> lampHueColor((wh * 256).roundToInt() and 0xFF, (ws * 255).roundToInt()) },
            ring = true,
            onChange = { wh, _ ->
                h = wh
                onChange((wh * 256).roundToInt() and 0xFF)
            }
        )
    }
}

@Composable
private fun PickerDialog(
    title: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.color_done)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) } }
    )
}

/**
 * Hue around (0 at 3 o'clock, clockwise), saturation from the white center to the rim. Tap or
 * drag anywhere; outside the rim counts as the rim. A [ring] has the hue only: the saturation
 * passed to [onChange] is always 1, the marker stays on the ring.
 */
@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    colorAt: (hue: Float, saturation: Float) -> Color,
    onChange: (hue: Float, saturation: Float) -> Unit,
    dim: Float = 0f,
    ring: Boolean = false,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val rim = remember { (0..WHEEL_STOPS).map { colorAt(it.toFloat() / WHEEL_STOPS, 1f) } }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    fun pick(position: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = min(size.width, size.height) / 2f
                        val d = position - center
                        val angle = atan2(d.y, d.x) / (2 * PI).toFloat()
                        val s = if (ring) 1f else (hypot(d.x, d.y) / radius).coerceIn(0f, 1f)
                        currentOnChange((angle + 1f) % 1f, s)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pick(down.position)
                        down.consume()
                        drag(down.id) { change ->
                            pick(change.position)
                            change.consume()
                        }
                    }
                }
        ) {
            val radius = size.minDimension / 2f
            val ringWidth = RING_WIDTH.toPx()
            val markerRadius: Float
            if (ring) {
                drawCircle(Brush.sweepGradient(rim, center), radius - ringWidth / 2, style = Stroke(ringWidth))
                markerRadius = radius - ringWidth / 2
            } else {
                drawCircle(Brush.sweepGradient(rim, center), radius)
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, radius), radius)
                if (dim > 0f) drawCircle(Color.Black.copy(alpha = dim.coerceIn(0f, 1f)), radius)
                markerRadius = saturation * radius
            }

            val angle = hue * 2 * PI
            val marker = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * markerRadius
            if (ring) drawCircle(colorAt(hue, 1f), radius - ringWidth - 16.dp.toPx())
            drawCircle(Color.Black, 12.dp.toPx(), marker, style = Stroke(4.dp.toPx()))
            drawCircle(Color.White, 12.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun Swatch(color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
    )
}

/** A color sample to open a picker with. */
@Composable
fun ColorButton(color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) color else color.copy(alpha = 0.38f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
    )
}

/** A track filled with [brush] for a Material slider. */
@Composable
fun GradientTrack(brush: Brush, enabled: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(brush, alpha = if (enabled) 1f else 0.38f)
    )
}

private const val WHEEL_STOPS = 48
private val RING_WIDTH = 40.dp

/** Candle, warm, neutral and cold whites, then a few pure colors. */
private val PRESETS = listOf(0xFF9329, 0xFF9F46, 0xFFD6AA, 0xFFFFFF, 0xC9E2FF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFF00FF)
