package com.brkckr.parkv3.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brkckr.parkv3.R
import com.brkckr.parkv3.domain.model.Occupancy
import com.brkckr.parkv3.navigation.openDirections
import com.brkckr.parkv3.ui.components.StatusBadge
import com.brkckr.parkv3.ui.components.amountText
import com.brkckr.parkv3.ui.components.occupancyText
import com.brkckr.parkv3.ui.components.parkName
import com.brkckr.parkv3.ui.components.refreshErrorText
import com.brkckr.parkv3.ui.components.rememberNow
import com.brkckr.parkv3.ui.components.sourceTimestampText
import com.brkckr.parkv3.ui.components.timeWithRelative
import com.brkckr.parkv3.ui.main.EmptyState
import kotlinx.coroutines.launch

@Composable
fun DetailRoute(
    onBack: () -> Unit,
    onShowOnMap: (Int) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onBackground() }
    }
    DetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onToggleFavorite = viewModel::onToggleFavorite,
        onDirections = {
            val location = state.location
            val label = state.name ?: resources.getString(R.string.park_unnamed, state.parkId)
            if (location == null || !context.openDirections(location, label)) {
                scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.directions_no_app)) }
            }
        },
        onShowOnMap = if (viewModel.canShowOnMap) {
            { onShowOnMap(state.parkId) }
        } else {
            null
        },
    )
}

@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDirections: () -> Unit,
    onShowOnMap: (() -> Unit)?,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.park != null || state.detail != null) parkName(state.name, state.parkId) else stringResource(R.string.detail_title),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRetry, enabled = !state.isRefreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconToggleButton(checked = state.isFavorite, onCheckedChange = { onToggleFavorite() }) {
                        Icon(
                            if (state.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(if (state.isFavorite) R.string.favorite_remove else R.string.favorite_add),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val content = state.content) {
                DetailContent.Loading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(16.dp))
                    Text(stringResource(R.string.detail_loading))
                }
                is DetailContent.Error -> EmptyState(
                    icon = Icons.Filled.ErrorOutline,
                    title = stringResource(R.string.detail_error_title),
                    body = stringResource(R.string.detail_error_body, refreshErrorText(content.error)),
                ) { OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) } }
                // Readable line length on tablets and in landscape.
                DetailContent.ListDataOnly, DetailContent.Full -> DetailBody(
                    state = state,
                    onRetry = onRetry,
                    onDirections = onDirections,
                    onShowOnMap = onShowOnMap,
                    modifier = Modifier.widthIn(max = DETAIL_MAX_WIDTH).align(Alignment.TopCenter),
                )
            }
            if (state.isRefreshing && state.content != DetailContent.Loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(
    state: DetailUiState,
    onRetry: () -> Unit,
    onDirections: () -> Unit,
    onShowOnMap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow(state.detail?.fetchedAtMillis, state.listUpdatedAtMillis)
    val detail = state.detail
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        if (!state.isListed && state.park != null) Notice(stringResource(R.string.detail_missing_from_source))
        when {
            detail == null && state.error != null -> Notice(
                stringResource(R.string.detail_partial_from_list, refreshErrorText(state.error)),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
            detail == null && state.isRefreshing -> Notice(stringResource(R.string.detail_loading))
            detail != null && state.error != null -> Notice(
                stringResource(R.string.detail_offline_cached, timeWithRelative(detail.fetchedAtMillis, now)) +
                    " (" + refreshErrorText(state.error) + ")",
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                parkName(state.name, state.parkId),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp).semantics { heading() },
            )
            Text(
                state.district ?: stringResource(R.string.district_unknown),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section(stringResource(R.string.detail_status)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(state.availability)
                    Text(occupancyText(state.occupancy, state.capacity), style = MaterialTheme.typography.bodyLarge)
                }
                state.listUpdatedAtMillis?.let {
                    Text(
                        stringResource(R.string.detail_status_source, timeWithRelative(it, now)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            val occupancy = state.occupancy
            if (occupancy is Occupancy.Known) {
                Section(stringResource(R.string.detail_occupancy)) {
                    InfoRow(stringResource(R.string.detail_capacity), occupancy.capacity.toString())
                    InfoRow(stringResource(R.string.detail_free_spaces), occupancy.empty.toString())
                }
            }

            detail?.address?.let { Section(stringResource(R.string.detail_address)) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            state.workHours?.let { Section(stringResource(R.string.detail_work_hours)) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            state.parkType?.let { Section(stringResource(R.string.detail_park_type)) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
            state.freeTime?.let {
                Section(stringResource(R.string.detail_free_time)) {
                    Text(it.toString(), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.detail_free_time_note), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (detail != null) {
                Section(stringResource(R.string.detail_tariffs)) {
                    if (detail.tariffLines.isEmpty()) {
                        Text(stringResource(R.string.detail_tariffs_missing), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        detail.tariffLines.forEach { line -> InfoRow(line.label, line.value.orEmpty()) }
                    }
                }
                Section(stringResource(R.string.detail_monthly_fee)) {
                    val fee = detail.monthlyFee
                    Text(
                        if (fee != null && fee > 0) amountText(fee) else stringResource(R.string.detail_monthly_fee_missing),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        stringResource(R.string.detail_fetched_at, timeWithRelative(detail.fetchedAtMillis, now)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        detail.sourceUpdatedAt?.let {
                            stringResource(R.string.detail_source_updated_at, sourceTimestampText(it.epochMillis, it.raw))
                        } ?: stringResource(R.string.detail_source_updated_unknown),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.size(16.dp))
            if (state.location != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDirections) { Text(stringResource(R.string.action_directions)) }
                    if (onShowOnMap != null) OutlinedButton(onClick = onShowOnMap) { Text(stringResource(R.string.action_show_on_map)) }
                }
            } else {
                Text(stringResource(R.string.directions_unavailable_location), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(R.string.data_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        content()
    }
}

/** Label/value pair that stacks gracefully at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoRow(label: String, value: String) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 16.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Notice(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private val DETAIL_MAX_WIDTH: Dp = 640.dp
