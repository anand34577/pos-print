package com.posprint.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.posprint.app.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.posprint.app.domain.model.ConnectionState
import com.posprint.app.domain.model.JobStatus
import com.posprint.app.domain.model.PrintJob

@Composable
fun ConnectionChip(state: ConnectionState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    data class Look(val label: String, val dot: Color, val bg: Color, val fg: Color)
    val look = when (state) {
        ConnectionState.CONNECTED -> Look("Connected", scheme.primary, scheme.primaryContainer, scheme.onPrimaryContainer)
        ConnectionState.CONNECTING -> Look("Connecting…", scheme.tertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        ConnectionState.ERROR -> Look("Connection error", scheme.error, scheme.errorContainer, scheme.onErrorContainer)
        ConnectionState.DISCONNECTED -> Look("Not connected", scheme.outline, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
    }
    // A tinted container per state reads on any card colour in light and dark; the dot repeats the state for quick scanning.
    Surface(
        shape = MaterialTheme.shapes.small,
        color = look.bg,
        contentColor = look.fg,
        modifier = modifier.semantics { contentDescription = "Printer status: ${look.label}" },
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state == ConnectionState.CONNECTING) {
                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = look.dot)
            } else {
                Box(Modifier.size(8.dp).background(look.dot, CircleShape))
            }
            Spacer(Modifier.width(6.dp))
            Text(look.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun JobRow(job: PrintJob, onRetry: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (job.status) {
            JobStatus.PRINTING -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
            else -> {
                val (icon, tint) = jobIcon(job.status)
                Icon(icon, contentDescription = job.status.name.lowercase(), tint = tint ?: scheme.outline)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(job.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when (job.status) {
                JobStatus.QUEUED -> "Waiting…"
                JobStatus.PRINTING -> "Printing" + if (job.attempts > 1) " (attempt ${job.attempts})" else ""
                JobStatus.DONE -> "Sent to printer"
                JobStatus.CANCELLED -> "Cancelled"
                JobStatus.FAILED -> job.error ?: "Failed"
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (job.status == JobStatus.FAILED) scheme.error else scheme.onSurfaceVariant,
            )
        }
        when (job.status) {
            JobStatus.FAILED, JobStatus.CANCELLED -> TextButton(onRetry) { Text("Retry") }
            JobStatus.QUEUED -> TextButton(onCancel) { Text("Cancel") }
            else -> Unit
        }
    }
}

@Composable
private fun jobIcon(status: JobStatus): Pair<ImageVector, androidx.compose.ui.graphics.Color?> = when (status) {
    JobStatus.DONE -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
    JobStatus.FAILED -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
    else -> Icons.Rounded.Schedule to null
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, contentDescription = null, Modifier.padding(20.dp).size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.size(20.dp))
            action()
        }
    }
}

/** Icon on a soft tinted square: the visual anchor for list cards (printers, templates, actions). */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Surface(modifier.size(size), shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, Modifier.size(size * 0.55f)) }
    }
}
