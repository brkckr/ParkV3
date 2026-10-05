package com.brkckr.parkv3.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brkckr.parkv3.R
import com.brkckr.parkv3.domain.ParkFilters
import com.brkckr.parkv3.domain.model.FreshnessPolicy
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.location.LocationStatus
import com.brkckr.parkv3.ui.components.refreshErrorText
import com.brkckr.parkv3.ui.components.rememberNow
import com.brkckr.parkv3.ui.components.timeWithRelative

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().testTag(MainTestTags.SEARCH),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

/** Filter chips wrap onto new lines at large font sizes instead of being cut off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterRow(
    filters: ParkFilters,
    onToggleAvailable: () -> Unit,
    onToggleFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterToggle(stringResource(R.string.filter_available), filters.availableOnly, onToggleAvailable)
        FilterToggle(stringResource(R.string.filter_favorites), filters.favoritesOnly, onToggleFavorites)
    }
}

@Composable
private fun FilterToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

@Composable
fun ViewModeToggle(mode: ViewMode, onChange: (ViewMode) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = mode == ViewMode.MAP,
            onClick = { onChange(ViewMode.MAP) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp)) },
        ) { Text(stringResource(R.string.view_map)) }
        SegmentedButton(
            selected = mode == ViewMode.LIST,
            onClick = { onChange(ViewMode.LIST) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) },
        ) { Text(stringResource(R.string.view_list)) }
    }
}

/**
 * Freshness of the list content: last successful download (device time), whether it may be
 * out of date, and the last failure. Shown alongside content, never instead of it.
 */
@Composable
fun FreshnessBanner(sync: SyncInfo, isRefreshing: Boolean, hasContent: Boolean, modifier: Modifier = Modifier) {
    val now = rememberNow(sync.lastSuccessAtMillis, sync.lastAttemptAtMillis)
    val stale = hasContent && FreshnessPolicy.isListStale(sync, now)
    val error = sync.lastError
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (stale) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(MainTestTags.FRESHNESS),
        ) {
            val updated = sync.lastSuccessAtMillis?.let { stringResource(R.string.freshness_updated, timeWithRelative(it, now)) }
                ?: stringResource(R.string.freshness_never)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isRefreshing) Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = if (isRefreshing) stringResource(R.string.freshness_refreshing) + " · " + updated else updated,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (hasContent && error == RefreshError.Network) {
                Text(stringResource(R.string.freshness_offline), style = MaterialTheme.typography.bodySmall)
            } else if (hasContent && error != null) {
                Text(stringResource(R.string.freshness_failed, refreshErrorText(error)), style = MaterialTheme.typography.bodySmall)
            }
            if (stale) {
                Text(stringResource(R.string.freshness_stale), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Sorting reference ("by distance from the destination") with a way to clear the destination. */
@Composable
fun ReferenceRow(reference: ReferencePoint?, onClearDestination: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = when (reference) {
            null -> stringResource(R.string.sort_by_name)
            is ReferencePoint.Destination -> stringResource(R.string.sort_by_destination)
            is ReferencePoint.UserLocation -> if (reference.approximate) {
                stringResource(R.string.sort_by_location_approximate)
            } else {
                stringResource(R.string.sort_by_location)
            }
        }
        if (reference is ReferencePoint.Destination) {
            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        if (reference is ReferencePoint.Destination) {
            TextButton(onClick = onClearDestination) { Text(stringResource(R.string.destination_clear)) }
        }
    }
}

/** Location outcome messages, each with the action that can resolve it. */
@Composable
fun LocationMessage(
    status: LocationStatus,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (text, action) = when (status) {
        LocationStatus.Idle -> return
        LocationStatus.Locating -> stringResource(R.string.location_locating) to null
        is LocationStatus.Available -> if (status.approximate) stringResource(R.string.location_approximate) to null else return
        LocationStatus.PermissionDenied -> stringResource(R.string.location_denied) to null
        LocationStatus.PermissionPermanentlyDenied ->
            stringResource(R.string.location_permanently_denied) to (stringResource(R.string.action_open_settings) to onOpenAppSettings)
        LocationStatus.ServicesDisabled ->
            stringResource(R.string.location_services_disabled) to (stringResource(R.string.action_location_settings) to onOpenLocationSettings)
        LocationStatus.Unavailable -> stringResource(R.string.location_unavailable) to (stringResource(R.string.action_retry) to onRetry)
    }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status == LocationStatus.Locating) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    if (status is LocationStatus.Available) Icons.Filled.MyLocation else Icons.Filled.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
            if (status != LocationStatus.Locating) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_close))
                }
            }
        }
    }
}

@Composable
fun ListContentState(
    content: ListContent,
    filtersActive: Boolean,
    onRetry: () -> Unit,
    onClearQuery: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (content) {
        ListContent.Loading -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.empty_loading_title), style = MaterialTheme.typography.titleMedium)
        }
        ListContent.OfflineWithoutCache -> EmptyState(
            icon = Icons.Filled.CloudOff,
            title = stringResource(R.string.empty_offline_title),
            body = stringResource(R.string.empty_offline_body),
            modifier = modifier,
        ) { OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } }
        is ListContent.SourceErrorWithoutCache -> EmptyState(
            icon = Icons.Filled.ErrorOutline,
            title = stringResource(R.string.empty_error_title),
            body = stringResource(R.string.empty_error_body, refreshErrorText(content.error)),
            modifier = modifier,
        ) { OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } }
        ListContent.NoFavorites -> EmptyState(
            icon = Icons.Filled.StarBorder,
            title = stringResource(R.string.empty_favorites_title),
            body = stringResource(R.string.empty_favorites_body),
            modifier = modifier,
        ) { OutlinedButton(onClick = onClearFilters) { Text(stringResource(R.string.filters_clear)) } }
        is ListContent.NoSearchResults -> EmptyState(
            icon = Icons.Filled.SearchOff,
            title = stringResource(R.string.empty_search_title, content.query),
            body = stringResource(R.string.empty_search_body),
            modifier = modifier,
        ) {
            OutlinedButton(onClick = onClearQuery) { Text(stringResource(R.string.search_clear)) }
            if (filtersActive) TextButton(onClick = onClearFilters) { Text(stringResource(R.string.filters_clear)) }
        }
        ListContent.NoFilterResults -> EmptyState(
            icon = Icons.Filled.FilterAltOff,
            title = stringResource(R.string.empty_filters_title),
            body = stringResource(R.string.empty_filters_body),
            modifier = modifier,
        ) { OutlinedButton(onClick = onClearFilters) { Text(stringResource(R.string.filters_clear)) } }
        ListContent.Items -> Unit
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(MainTestTags.EMPTY_STATE),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.size(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
        Spacer(Modifier.size(16.dp))
        actions()
    }
}

object MainTestTags {
    const val SEARCH = "search"
    const val FRESHNESS = "freshness"
    const val EMPTY_STATE = "empty_state"
    const val PARK_LIST = "park_list"
    const val MAP = "map"
    const val MY_LOCATION = "my_location"
    fun parkRow(id: Int) = "park_row_$id"
    fun favoriteToggle(id: Int) = "favorite_$id"
}
