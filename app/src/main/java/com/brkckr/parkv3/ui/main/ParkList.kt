package com.brkckr.parkv3.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.brkckr.parkv3.R
import com.brkckr.parkv3.domain.ParkListItem
import com.brkckr.parkv3.domain.model.OrphanFavorite
import com.brkckr.parkv3.ui.components.StatusBadge
import com.brkckr.parkv3.ui.components.distanceText
import com.brkckr.parkv3.ui.components.occupancyText
import com.brkckr.parkv3.ui.components.parkName

@Composable
fun ParkList(
    items: List<ParkListItem>,
    orphanFavorites: List<OrphanFavorite>,
    showOrphans: Boolean,
    selectedParkId: Int?,
    isRefreshing: Boolean,
    canShowOnMap: Boolean,
    /** Changing search or filters starts the result list from the top. */
    scrollResetKey: Any,
    onRefresh: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onShowOnMap: (Int) -> Unit,
    onToggleFavorite: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollResetKey) { listState.scrollToItem(0) }
    // Keep the shared selection visible when arriving from the map.
    LaunchedEffect(selectedParkId) {
        val index = items.indexOfFirst { it.park.id == selectedParkId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(MainTestTags.PARK_LIST),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.park.id }) { item ->
                ParkRow(
                    item = item,
                    selected = item.park.id == selectedParkId,
                    canShowOnMap = canShowOnMap && item.park.location != null,
                    onClick = { onOpenDetail(item.park.id) },
                    onShowOnMap = { onShowOnMap(item.park.id) },
                    onToggleFavorite = { onToggleFavorite(item.park.id, !item.isFavorite) },
                )
            }
            item(key = "attribution") {
                Text(
                    stringResource(R.string.data_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (showOrphans && orphanFavorites.isNotEmpty()) {
                item(key = "orphans_header") {
                    Text(
                        stringResource(R.string.orphan_favorites_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp).semantics { heading() },
                    )
                }
                items(orphanFavorites, key = { "orphan_${it.parkId}" }) { orphan ->
                    ListItem(
                        headlineContent = { Text(orphan.name ?: stringResource(R.string.orphan_favorite_unnamed, orphan.parkId)) },
                        supportingContent = if (orphan.district != null) {
                            { Text(orphan.district) }
                        } else {
                            null
                        },
                        trailingContent = {
                            IconToggleButton(checked = true, onCheckedChange = { onToggleFavorite(orphan.parkId, false) }) {
                                Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.favorite_remove))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParkRow(
    item: ParkListItem,
    selected: Boolean,
    canShowOnMap: Boolean,
    onClick: () -> Unit,
    onShowOnMap: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val park = item.park
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .testTag(MainTestTags.parkRow(park.id)),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(parkName(park.name, park.id), style = MaterialTheme.typography.titleMedium)
                    Text(
                        park.district ?: stringResource(R.string.district_unknown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconToggleButton(
                    checked = item.isFavorite,
                    onCheckedChange = { onToggleFavorite() },
                    modifier = Modifier.testTag(MainTestTags.favoriteToggle(park.id)),
                ) {
                    Icon(
                        if (item.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(if (item.isFavorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, end = 12.dp),
            ) {
                StatusBadge(park.availability)
                Text(occupancyText(park.occupancy, park.capacity), style = MaterialTheme.typography.bodyMedium)
                val distance = item.distanceMeters?.let { distanceText(it) }
                    ?: if (park.location == null) stringResource(R.string.distance_unknown_location) else null
                if (distance != null) {
                    Text(distance, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (canShowOnMap) {
                TextButton(onClick = onShowOnMap) { Text(stringResource(R.string.action_show_on_map)) }
            }
        }
    }
}
