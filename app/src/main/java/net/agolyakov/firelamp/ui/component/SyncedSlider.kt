package net.agolyakov.firelamp.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import net.agolyakov.firelamp.ui.color.GradientTrack
import kotlin.math.roundToInt

/**
 * An integer slider over a value that lives on the lamp.
 *
 * While dragged it shows the finger's value and reports every change ([onValueChange] is
 * expected to throttle the writes); after release it keeps that value until the lamp reports
 * it back (or [SETTLE_TIMEOUT_MS] passes, e.g. when the write was rejected), so the thumb does
 * not jump back to the old value in between.
 */
@OptIn(ExperimentalMaterial3Api::class) // the Slider overload with a custom track
@Composable
fun SyncedSlider(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Shown after the slider, gets the displayed value (the finger's while dragging). */
    valueLabel: (@Composable (Int) -> Unit)? = null,
    /** Fills the track instead of the active/inactive colors (a hue slider shows the rainbow). */
    trackBrush: Brush? = null,
) {
    var local by remember { mutableStateOf<Int?>(null) }
    var dragging by remember { mutableStateOf(false) }

    // The lamp's value arrived (or changed elsewhere): drop the local one unless still dragging
    LaunchedEffect(value) {
        if (!dragging) local = null
    }
    LaunchedEffect(dragging, local) {
        if (!dragging && local != null) {
            delay(SETTLE_TIMEOUT_MS)
            local = null
        }
    }

    val shown = (local ?: value).coerceIn(range)
    val span = range.last - range.first
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        val sliderEnabled = enabled && span > 0
        val onChange: (Float) -> Unit = {
            val v = it.roundToInt()
            dragging = true
            if (v != local) {
                local = v
                onValueChange(v)
            }
        }
        val valueRange = range.first.toFloat()..range.last.toFloat()
        // Short ranges (modes, palettes) snap to their values
        val steps = if (span in 2..20) span - 1 else 0
        if (trackBrush != null) {
            Slider(
                value = shown.toFloat(),
                onValueChange = onChange,
                onValueChangeFinished = { dragging = false },
                valueRange = valueRange,
                steps = steps,
                enabled = sliderEnabled,
                track = { GradientTrack(trackBrush, sliderEnabled) },
                modifier = Modifier.weight(1f)
            )
        } else Slider(
            value = shown.toFloat(),
            onValueChange = onChange,
            onValueChangeFinished = { dragging = false },
            valueRange = valueRange,
            steps = steps,
            enabled = sliderEnabled,
            modifier = Modifier.weight(1f)
        )
        valueLabel?.invoke(shown)
    }
}

private const val SETTLE_TIMEOUT_MS = 1500L
