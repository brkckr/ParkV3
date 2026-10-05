package com.brkckr.parkv3.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brkckr.parkv3.R
import com.brkckr.parkv3.domain.ParkListItem
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.location.LocationStatus
import com.brkckr.parkv3.ui.components.StatusBadge
import com.brkckr.parkv3.ui.components.distanceText
import com.brkckr.parkv3.ui.components.occupancyText
import com.brkckr.parkv3.ui.components.parkName
import com.brkckr.parkv3.ui.components.statusStyle
import com.brkckr.parkv3.ui.main.EmptyState
import com.brkckr.parkv3.ui.main.MainTestTags
import com.brkckr.parkv3.ui.main.MainUiState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState

private val ISTANBUL = LatLng(41.0082, 28.9784)

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

/** Map adapter for a list item; [selected] is part of equality so the pin re-renders. */
private data class ParkClusterItem(val item: ParkListItem, val selected: Boolean) : ClusterItem {
    override val position: LatLng = item.park.location!!.toLatLng()
    override val title: String? get() = item.park.name
    override val snippet: String? get() = item.park.district
    override val zIndex: Float get() = if (selected) 1f else 0f
}

@Composable
fun ParkMapPane(
    state: MainUiState,
    onSelectPark: (Int?) -> Unit,
    onSetDestination: (GeoPoint) -> Unit,
    onOpenDetail: (Int) -> Unit,
    onDirections: (ParkListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.mapStatus != MapStatus.AVAILABLE) {
        EmptyState(
            icon = Icons.Filled.Map,
            title = stringResource(R.string.map_unavailable_title),
            body = stringResource(
                if (state.mapStatus == MapStatus.NO_API_KEY) R.string.map_unavailable_no_key else R.string.map_unavailable_no_services,
            ),
            modifier = modifier,
        ) {}
        return
    }
    GoogleParkMap(state, onSelectPark, onSetDestination, onOpenDetail, onDirections, modifier)
}

@OptIn(MapsComposeExperimentalApi::class, ExperimentalLayoutApi::class)
@Composable
private fun GoogleParkMap(
    state: MainUiState,
    onSelectPark: (Int?) -> Unit,
    onSetDestination: (GeoPoint) -> Unit,
    onOpenDetail: (Int) -> Unit,
    onDirections: (ParkListItem) -> Unit,
    modifier: Modifier,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(ISTANBUL, 10f)
    }
    // Invalid coordinates are never drawn (docs/adr/0003).
    val clusterItems = remember(state.items, state.selectedParkId) {
        state.items.filter { it.park.location != null }.map { ParkClusterItem(it, it.park.id == state.selectedParkId) }
    }
    val hiddenCount = state.items.size - clusterItems.size
    val selected = state.selectedItem
    val userPoint = (state.location as? LocationStatus.Available)?.point

    LaunchedEffect(state.selectedParkId) {
        selected?.park?.location?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), 15f), durationMs = 600)
        }
    }
    LaunchedEffect(userPoint) {
        userPoint?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it.toLatLng(), 14f), durationMs = 600) }
    }

    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize().testTag(MainTestTags.MAP),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            onMapClick = { onSelectPark(null) },
            onMapLongClick = { onSetDestination(GeoPoint(it.latitude, it.longitude)) },
        ) {
            Clustering(
                items = clusterItems,
                onClusterClick = { cluster ->
                    cameraPositionState.move(
                        CameraUpdateFactory.newLatLngZoom(cluster.position, cameraPositionState.position.zoom + 2f),
                    )
                    true
                },
                onClusterItemClick = { clusterItem ->
                    onSelectPark(clusterItem.item.park.id)
                    true
                },
                clusterContent = { cluster -> ClusterBubble(cluster.size) },
                clusterItemContent = { clusterItem -> ParkPin(clusterItem.item, clusterItem.selected) },
            )
            state.destination?.let { destination ->
                val markerState = remember(destination) { MarkerState(position = destination.toLatLng()) }
                Marker(
                    state = markerState,
                    title = stringResource(R.string.map_destination_marker),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                )
            }
            userPoint?.let { point ->
                Circle(
                    center = point.toLatLng(),
                    radius = 40.0,
                    fillColor = Color(0x553D7BFF),
                    strokeColor = Color(0xFF1453A3),
                    strokeWidth = 4f,
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Accessible alternative to long-press: use the map centre as destination.
            FilledTonalButton(onClick = {
                val target = cameraPositionState.position.target
                onSetDestination(GeoPoint(target.latitude, target.longitude))
            }) {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.map_set_destination), modifier = Modifier.padding(start = 6.dp))
            }
            if (hiddenCount > 0) {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = MaterialTheme.shapes.small) {
                    Text(
                        pluralStringResource(R.plurals.map_hidden_without_location, hiddenCount, hiddenCount),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
        // Map centre crosshair for the "use map centre" action.
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        if (selected != null) {
            SelectedParkCard(
                item = selected,
                onOpenDetail = { onOpenDetail(selected.park.id) },
                onDirections = { onDirections(selected) },
                onClose = { onSelectPark(null) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 88.dp),
            )
        } else {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 88.dp, bottom = 24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    stringResource(R.string.map_destination_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

/** Status colour + icon pin; the favorite star sits outside the status disc so it never hides it. */
@Composable
private fun ParkPin(item: ParkListItem, selected: Boolean) {
    val style = statusStyle(item.park.availability)
    val size = if (selected) 40.dp else 32.dp
    Box(modifier = Modifier.size(size + 10.dp)) {
        Surface(
            shape = CircleShape,
            color = style.container,
            contentColor = style.content,
            border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else style.content),
            modifier = Modifier.size(size).align(Alignment.BottomStart),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(style.icon, contentDescription = null, modifier = Modifier.size(size * 0.55f))
            }
        }
        if (item.isFavorite) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp).align(Alignment.TopEnd).offset(x = 0.dp, y = 0.dp),
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.padding(2.dp))
            }
        }
    }
}

@Composable
private fun ClusterBubble(count: Int) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(count.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedParkCard(
    item: ParkListItem,
    onOpenDetail: () -> Unit,
    onDirections: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val park = item.park
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(parkName(park.name, park.id), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_close)) }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                StatusBadge(park.availability)
                Text(occupancyText(park.occupancy, park.capacity), style = MaterialTheme.typography.bodyMedium)
                item.distanceMeters?.let { Text(distanceText(it), style = MaterialTheme.typography.bodyMedium) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, end = 12.dp)) {
                OutlinedButton(onClick = onOpenDetail) { Text(stringResource(R.string.action_details)) }
                TextButton(onClick = onDirections) { Text(stringResource(R.string.action_directions)) }
            }
        }
    }
}
