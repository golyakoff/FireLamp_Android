package net.agolyakov.firelamp.ui.screen.firmware

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.agolyakov.firelamp.R

// The three forms of a firmware update, whichever way it goes (from the phone over Bluetooth, or
// by the lamp itself over WiFi): a question with buttons, the progress, and how it ended.

/** A button of an update screen. */
data class UpdateAction(val label: String, val onClick: () -> Unit)

/** The background and centering of an update screen, a whole screen of its own. */
@Composable
fun UpdateScreenFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** A question: a title, a few lines (versions, size) and what to do. */
@Composable
fun UpdatePromptScreen(
    title: String,
    lines: List<String> = emptyList(),
    primary: UpdateAction,
    secondary: UpdateAction? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (lines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            lines.forEach { line ->
                Text(text = line, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        UpdateButtons(primary, secondary)
    }
}

/**
 * The update going on.
 * @param progress 0..100, null when it is not known (the indicator spins)
 * @param detail what is done right now
 * @param note a hint below, e.g. that the screen can be closed
 * @param close a way out while it goes on
 */
@Composable
fun UpdateProgressScreen(
    title: String,
    progress: Float? = null,
    detail: String? = null,
    note: String? = null,
    close: UpdateAction? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress != null) {
            CircularProgressIndicator(
                modifier = Modifier.size(72.dp),
                strokeWidth = 10.dp,
                progress = { (progress.coerceIn(0f, 100f)) / 100f }
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(72.dp), strokeWidth = 10.dp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (progress != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${"%.1f".format(progress.coerceIn(0f, 100f))}%",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!detail.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (note != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (close != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = close.onClick, modifier = Modifier.fillMaxWidth(0.85f)) {
                Text(text = close.label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** How it ended: the icon and the title say whether it went well, the message gives the details. */
@Composable
fun UpdateResultScreen(
    success: Boolean,
    title: String,
    message: String? = null,
    primary: UpdateAction,
    secondary: UpdateAction? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = stringResource(if (success) R.string.update_success else R.string.update_failure),
            tint = if (success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = if (success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        )

        if (message != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        UpdateButtons(primary, secondary, tertiary = success)
    }
}

@Composable
private fun UpdateButtons(primary: UpdateAction, secondary: UpdateAction?, tertiary: Boolean = false) {
    Button(
        onClick = primary.onClick,
        modifier = Modifier.fillMaxWidth(0.85f),
        colors = if (tertiary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        } else {
            ButtonDefaults.buttonColors()
        }
    ) {
        Text(text = primary.label, style = MaterialTheme.typography.titleMedium)
    }
    if (secondary != null) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = secondary.onClick, modifier = Modifier.fillMaxWidth(0.85f)) {
            Text(text = secondary.label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
