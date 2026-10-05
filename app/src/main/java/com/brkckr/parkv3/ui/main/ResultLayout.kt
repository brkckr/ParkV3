package com.brkckr.parkv3.ui.main

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.brkckr.parkv3.ui.map.MapStatus

/** How the result area under the search and filter header is arranged. */
enum class ResultLayout {
    LIST,
    MAP,
    /** List and map side by side, sharing the selection. */
    LIST_AND_MAP,
    /** An empty, loading or error state instead of results. */
    MESSAGE,
}

/** From this window width the list and the map fit side by side (Material "expanded"). */
val TWO_PANE_MIN_WIDTH: Dp = 840.dp

fun resultLayout(width: Dp, mapStatus: MapStatus, viewMode: ViewMode, content: ListContent): ResultLayout = when {
    content != ListContent.Items -> ResultLayout.MESSAGE
    mapStatus != MapStatus.AVAILABLE -> ResultLayout.LIST
    width >= TWO_PANE_MIN_WIDTH -> ResultLayout.LIST_AND_MAP
    viewMode == ViewMode.MAP -> ResultLayout.MAP
    else -> ResultLayout.LIST
}

/** The map/list switch is only needed when the two cannot be shown together. */
fun showsViewModeToggle(width: Dp, mapStatus: MapStatus): Boolean =
    mapStatus == MapStatus.AVAILABLE && width < TWO_PANE_MIN_WIDTH

/** List pane in the two-pane layout: 40% of the width, kept between 320 and 440 dp. */
fun listPaneWidth(totalWidth: Dp): Dp = (totalWidth * 0.4f).coerceIn(320.dp, 440.dp)
