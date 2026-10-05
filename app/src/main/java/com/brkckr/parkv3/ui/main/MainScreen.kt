package com.brkckr.parkv3.ui.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brkckr.parkv3.R
import com.brkckr.parkv3.domain.ParkListItem
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.navigation.RESULT_SHOW_ON_MAP
import com.brkckr.parkv3.navigation.openDirections
import com.brkckr.parkv3.navigation.tryStartActivity
import com.brkckr.parkv3.ui.components.refreshErrorMessage
import com.brkckr.parkv3.ui.map.MapStatus
import com.brkckr.parkv3.ui.map.ParkMapPane
import kotlinx.coroutines.launch

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** Callbacks of the main screen, kept together so the screen itself stays stateless. */
class MainActions(
    val onQueryChange: (String) -> Unit = {},
    val onClearQuery: () -> Unit = {},
    val onToggleOpen: () -> Unit = {},
    val onToggleAvailable: () -> Unit = {},
    val onToggleFavorites: () -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onViewModeChange: (ViewMode) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onSelectPark: (Int?) -> Unit = {},
    val onShowOnMap: (Int) -> Unit = {},
    val onOpenDetail: (Int) -> Unit = {},
    val onToggleFavorite: (Int, Boolean) -> Unit = { _, _ -> },
    val onSetDestination: (GeoPoint) -> Unit = {},
    val onClearDestination: () -> Unit = {},
    val onDirections: (ParkListItem) -> Unit = {},
    val onLocate: () -> Unit = {},
    val onDismissLocationMessage: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
    val onOpenLocationSettings: () -> Unit = {},
)

@Composable
fun MainRoute(
    resultHandle: SavedStateHandle?,
    onOpenDetail: (Int) -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRationale by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val activity = context.findActivity()
        val canAskAgain = activity == null || LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        viewModel.onLocationPermissionResult(granted = result.values.any { it }, canAskAgain = canAskAgain)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is MainEvent.RefreshFailed -> scope.launch {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.refresh_failed_snackbar, resources.refreshErrorMessage(event.error)),
                    )
                }
                MainEvent.RefreshPartial -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getString(R.string.refresh_partial_snackbar))
                }
                MainEvent.RequestLocationPermission -> {
                    val activity = context.findActivity()
                    if (activity != null && LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
                        showRationale = true
                    } else {
                        permissionLauncher.launch(LOCATION_PERMISSIONS)
                    }
                }
            }
        }
    }

    // "Show on map" requested from the detail screen.
    if (resultHandle != null) {
        val showOnMap by resultHandle.getStateFlow<Int?>(RESULT_SHOW_ON_MAP, null).collectAsStateWithLifecycle()
        LaunchedEffect(showOnMap) {
            showOnMap?.let {
                viewModel.onShowOnMap(it)
                resultHandle[RESULT_SHOW_ON_MAP] = null
            }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.location_rationale_title)) },
            text = { Text(stringResource(R.string.location_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(LOCATION_PERMISSIONS)
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text(stringResource(R.string.action_not_now)) }
            },
        )
    }

    val actions = remember(viewModel, context, resources) {
        MainActions(
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            onToggleOpen = viewModel::onToggleOpenFilter,
            onToggleAvailable = viewModel::onToggleAvailableFilter,
            onToggleFavorites = viewModel::onToggleFavoritesFilter,
            onClearFilters = viewModel::onClearFilters,
            onViewModeChange = viewModel::onViewModeChange,
            onRefresh = viewModel::onRefresh,
            onSelectPark = viewModel::onSelectPark,
            onShowOnMap = viewModel::onShowOnMap,
            onOpenDetail = onOpenDetail,
            onToggleFavorite = viewModel::onToggleFavorite,
            onSetDestination = viewModel::onSetDestination,
            onClearDestination = viewModel::onClearDestination,
            onDirections = { item ->
                val location = item.park.location
                val label = item.park.name ?: resources.getString(R.string.park_unnamed, item.park.id)
                if (location == null || !context.openDirections(location, label)) {
                    scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.directions_no_app)) }
                }
            },
            onLocate = viewModel::onLocateRequested,
            onDismissLocationMessage = viewModel::onDismissLocationMessage,
            onOpenAppSettings = {
                context.tryStartActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onOpenLocationSettings = { context.tryStartActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        )
    }

    MainScreen(state = state, actions = actions, snackbarHostState = snackbarHostState)
}

@Composable
fun MainScreen(
    state: MainUiState,
    actions: MainActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    mapPane: @Composable (Modifier) -> Unit = { modifier ->
        ParkMapPane(
            state = state,
            onSelectPark = actions.onSelectPark,
            onSetDestination = actions.onSetDestination,
            onOpenDetail = actions.onOpenDetail,
            onDirections = actions.onDirections,
            modifier = modifier,
        )
    },
) {
    val hasContent = state.totalCount > 0
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onLocate,
                icon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
                text = { Text(stringResource(R.string.action_my_location)) },
                modifier = Modifier.testTag(MainTestTags.MY_LOCATION),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val width = maxWidth
            Column(Modifier.fillMaxSize()) {
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SearchField(
                                query = state.query,
                                onQueryChange = actions.onQueryChange,
                                onClear = actions.onClearQuery,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = actions.onRefresh, enabled = !state.isRefreshing) {
                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                            }
                        }
                        FilterRow(
                            filters = state.filters,
                            onToggleOpen = actions.onToggleOpen,
                            onToggleAvailable = actions.onToggleAvailable,
                            onToggleFavorites = actions.onToggleFavorites,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        ) {
                            if (hasContent) {
                                Text(
                                    pluralStringResource(R.plurals.result_count, state.totalCount, state.items.size, state.totalCount),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            if (showsViewModeToggle(width, state.mapStatus)) {
                                ViewModeToggle(state.viewMode, actions.onViewModeChange)
                            }
                        }
                    }
                }
                // Network activity never replaces existing content with a full-screen loader.
                if (state.isRefreshing && hasContent) LinearProgressIndicator(Modifier.fillMaxWidth())
                FreshnessBanner(sync = state.sync, isRefreshing = state.isRefreshing, hasContent = hasContent)
                LocationMessage(
                    status = state.location,
                    onOpenAppSettings = actions.onOpenAppSettings,
                    onOpenLocationSettings = actions.onOpenLocationSettings,
                    onRetry = actions.onLocate,
                    onDismiss = actions.onDismissLocationMessage,
                )
                if (hasContent) ReferenceRow(state.reference, actions.onClearDestination)

                val content = state.content
                val list: @Composable (Modifier) -> Unit = { modifier ->
                    ParkList(
                        items = state.items,
                        orphanFavorites = state.orphanFavorites,
                        showOrphans = state.filters.favoritesOnly,
                        selectedParkId = state.selectedParkId,
                        isRefreshing = state.isRefreshing,
                        canShowOnMap = state.mapStatus == MapStatus.AVAILABLE,
                        scrollResetKey = state.query to state.filters,
                        onRefresh = actions.onRefresh,
                        onOpenDetail = actions.onOpenDetail,
                        onShowOnMap = actions.onShowOnMap,
                        onToggleFavorite = actions.onToggleFavorite,
                        modifier = modifier,
                    )
                }
                when (resultLayout(width, state.mapStatus, state.viewMode, content)) {
                    ResultLayout.LIST_AND_MAP -> Row(Modifier.weight(1f).fillMaxWidth()) {
                        list(Modifier.width(listPaneWidth(width)).fillMaxHeight())
                        VerticalDivider()
                        mapPane(Modifier.weight(1f).fillMaxHeight())
                    }
                    ResultLayout.MAP -> mapPane(Modifier.weight(1f))
                    ResultLayout.LIST -> list(Modifier.weight(1f))
                    ResultLayout.MESSAGE -> ListContentState(
                        content = content,
                        filtersActive = state.filters.isAnyActive,
                        onRetry = actions.onRefresh,
                        onClearQuery = actions.onClearQuery,
                        onClearFilters = actions.onClearFilters,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
