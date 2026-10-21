package net.agolyakov.firelamp.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * An integer slider over a value that lives on the lamp.
 *
 * While dragged it shows the finger's value and reports every change ([onValueChange] is
 * expected to throttle the writes); after release it keeps that value until the lamp reports
 * it back (or [SETTLE_TIMEOUT_MS] passes, e.g. when the write was rejected), so the thumb does
 * not jump back to the old value in between.
 */
@Composable
fun SyncedSlider(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Shown after the slider, gets the displayed value (the finger's while dragging). */
    valueLabel: (@Composable (Int) -> Unit)? = null,
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
        Slider(
            value = shown.toFloat(),
            onValueChange = {
                val v = it.roundToInt()
                dragging = true
                if (v != local) {
                    local = v
                    onValueChange(v)
                }
            },
            onValueChangeFinished = { dragging = false },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // Short ranges (modes, palettes) snap to their values
            steps = if (span in 2..20) span - 1 else 0,
            enabled = enabled && span > 0,
            modifier = Modifier.weight(1f)
        )
        valueLabel?.invoke(shown)
    }
}

private const val SETTLE_TIMEOUT_MS = 1500L
