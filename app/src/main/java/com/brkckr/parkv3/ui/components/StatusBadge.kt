package com.brkckr.parkv3.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brkckr.parkv3.R
import com.brkckr.parkv3.domain.model.Availability
import com.brkckr.parkv3.ui.theme.LocalStatusColors

data class StatusStyle(val icon: ImageVector, val container: Color, val content: Color, val label: String)

/** Label of an availability, shared by status badges and map pin descriptions. */
@StringRes
fun availabilityLabel(availability: Availability): Int = when (availability) {
    Availability.AVAILABLE -> R.string.status_available
    Availability.FULL -> R.string.status_full
    Availability.CLOSED -> R.string.status_closed
    Availability.OPEN_OCCUPANCY_UNKNOWN -> R.string.status_open_unknown_occupancy
    Availability.UNKNOWN -> R.string.status_unknown
}

@Composable
fun statusStyle(availability: Availability): StatusStyle {
    val colors = LocalStatusColors.current
    val label = stringResource(availabilityLabel(availability))
    return when (availability) {
        Availability.AVAILABLE -> StatusStyle(Icons.Filled.CheckCircle, colors.availableContainer, colors.onAvailable, label)
        Availability.FULL -> StatusStyle(Icons.Filled.Block, colors.fullContainer, colors.onFull, label)
        Availability.CLOSED -> StatusStyle(Icons.Filled.Lock, colors.closedContainer, colors.onClosed, label)
        Availability.OPEN_OCCUPANCY_UNKNOWN -> StatusStyle(Icons.Filled.Info, colors.unknownContainer, colors.onUnknown, label)
        Availability.UNKNOWN -> StatusStyle(Icons.Filled.QuestionMark, colors.unknownContainer, colors.onUnknown, label)
    }
}

/** Status shown with icon + text + color, so color is never the only signal. */
@Composable
fun StatusBadge(availability: Availability, modifier: Modifier = Modifier) {
    val style = statusStyle(availability)
    Surface(color = style.container, contentColor = style.content, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(style.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(style.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
