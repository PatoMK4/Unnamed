package com.unnamed.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unnamed.app.ui.screens.CoachScreen
import com.unnamed.app.ui.screens.HistoryScreen
import com.unnamed.app.ui.screens.LogScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Log("log", "Log", Icons.AutoMirrored.Filled.Chat),
    History("history", "History", Icons.Filled.History),
    Coach("coach", "Coach", Icons.Filled.Insights),
}

@Composable
fun AppScaffold() {
    val nav = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val current by nav.currentBackStackEntryAsState()
                val route = current?.destination
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = route?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Log.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Log.route) { LogScreen() }
            composable(Tab.History.route) { HistoryScreen() }
            composable(Tab.Coach.route) { CoachScreen() }
        }
    }
}
