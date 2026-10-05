package com.brkckr.parkv3.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.brkckr.parkv3.ui.detail.DetailRoute
import com.brkckr.parkv3.ui.main.MainRoute
import kotlinx.serialization.Serializable

@Serializable
data object MainDestination

/** The argument name must stay in sync with DetailViewModel.ARG_PARK_ID. */
@Serializable
data class DetailDestination(val parkId: Int)

/** Result key: the detail screen asks the main screen to select a park on the map. */
const val RESULT_SHOW_ON_MAP = "show_on_map"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MainDestination) {
        composable<MainDestination> { entry ->
            MainRoute(
                resultHandle = entry.savedStateHandle,
                onOpenDetail = { parkId -> navController.navigate(DetailDestination(parkId)) },
            )
        }
        composable<DetailDestination> {
            DetailRoute(
                onBack = { navController.popBackStack() },
                onShowOnMap = { parkId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(RESULT_SHOW_ON_MAP, parkId)
                    navController.popBackStack()
                },
            )
        }
    }
}
