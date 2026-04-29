package com.codex.streetstrength.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.ui.calendar.CalendarScreenRoute
import com.codex.streetstrength.ui.library.LibraryScreenRoute
import com.codex.streetstrength.ui.overview.OverviewScreenRoute
import com.codex.streetstrength.ui.planner.PlannerScreenRoute
import com.codex.streetstrength.ui.training.TrainingScreenRoute
import java.time.LocalDate

@Composable
fun StreetStrengthRoot(app: StreetStrengthApp) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentRoute = destination?.route.orEmpty()
    val today = LocalDate.now().toString()
    val showBottomBar = !currentRoute.startsWith("training/")

    val bottomItems = listOf(
        BottomDestination(
            route = Routes.Calendar,
            label = "\u65e5\u5386",
            icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
        ),
        BottomDestination(
            route = Routes.Overview,
            label = "\u603b\u89c8",
            icon = { Icon(Icons.Rounded.Insights, contentDescription = null) },
        ),
        BottomDestination(
            route = Routes.planner(today),
            match = "planner/",
            label = "\u8ba1\u5212",
            icon = { Icon(Icons.Rounded.EditCalendar, contentDescription = null) },
        ),
        BottomDestination(
            route = Routes.Library,
            label = "\u8bad\u7ec3\u5e93",
            icon = { Icon(Icons.Rounded.AutoStories, contentDescription = null) },
        ),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomItems.forEach { item ->
                        val selected = destination?.hierarchy?.any {
                            when {
                                item.match != null -> it.route?.startsWith(item.match) == true
                                else -> it.route == item.route
                            }
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                }
                            },
                            icon = item.icon,
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Calendar,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.Calendar) {
                CalendarScreenRoute(
                    app = app,
                    onOpenPlanner = { date -> navController.navigate(Routes.planner(date)) },
                    onStartTraining = { date -> navController.navigate(Routes.training(date)) },
                )
            }
            composable(Routes.Overview) {
                OverviewScreenRoute(
                    app = app,
                    onOpenPlanner = { date -> navController.navigate(Routes.planner(date)) },
                )
            }
            composable(
                route = Routes.PlannerPattern,
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
            ) { entry ->
                val date = entry.arguments?.getString("date") ?: today
                PlannerScreenRoute(
                    app = app,
                    initialDate = date,
                    onOpenDate = { nextDate ->
                        navController.navigate(Routes.planner(nextDate)) {
                            launchSingleTop = true
                        }
                    },
                    onStartTraining = { nextDate -> navController.navigate(Routes.training(nextDate)) },
                )
            }
            composable(Routes.Library) {
                LibraryScreenRoute(app = app)
            }
            composable(
                route = Routes.TrainingPattern,
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
            ) { entry ->
                val date = entry.arguments?.getString("date") ?: today
                TrainingScreenRoute(
                    app = app,
                    date = date,
                    onBack = { navController.popBackStack() },
                    onWorkoutEnded = {
                        navController.navigate(Routes.Calendar) {
                            launchSingleTop = true
                            popUpTo(navController.graph.startDestinationId)
                        }
                    },
                )
            }
        }
    }
}

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
    val match: String? = null,
)

@Composable
inline fun <reified VM : ViewModel> rememberAppViewModel(
    key: String? = null,
    crossinline factory: () -> VM,
): VM {
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = factory() as T
        },
    )
}
