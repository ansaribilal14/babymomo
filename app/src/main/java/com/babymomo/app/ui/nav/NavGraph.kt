package com.babymomo.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.babymomo.app.ui.screens.chat.ChatScreen
import com.babymomo.app.ui.screens.heartbeat.HeartbeatScreen
import com.babymomo.app.ui.screens.interactive.InteractiveScreen
import com.babymomo.app.ui.screens.memory.MemoryScreen
import com.babymomo.app.ui.screens.mcp.McpScreen
import com.babymomo.app.ui.screens.models.ModelsScreen
import com.babymomo.app.ui.screens.projects.ProjectsScreen
import com.babymomo.app.ui.screens.settings.SettingsScreen
import com.babymomo.app.ui.screens.skills.SkillsScreen
import com.babymomo.app.ui.screens.terminal.TerminalScreen
import com.babymomo.app.ui.theme.ElectricTeal
import com.babymomo.app.ui.theme.DimBlue
import com.babymomo.app.ui.theme.MidnightBlack

data class BottomNavItem(
    val route: Any,
    val label: String,
    val icon: ImageVector
)

@Composable
fun BabymomoNavHost() {
    val navController = rememberNavController()
    val bottomItems = listOf(
        BottomNavItem(Route.Chat, "Chat", Icons.Filled.Chat),
        BottomNavItem(Route.Memory, "Memory", Icons.Filled.Memory),
        BottomNavItem(Route.Projects, "Projects", androidx.compose.material.icons.Icons.Filled.Folder),
        BottomNavItem(Route.Models, "Models", androidx.compose.material.icons.Icons.Filled.Storage),
        BottomNavItem(Route.Settings, "Settings", Icons.Filled.Settings)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MidnightBlack,
                contentColor = ElectricTeal
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any {
                            it.route == item.route::class.qualifiedName
                        } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricTeal,
                            selectedTextColor = ElectricTeal,
                            unselectedIconColor = DimBlue,
                            unselectedTextColor = DimBlue,
                            indicatorColor = ElectricTeal.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Chat,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Route.Chat> { ChatScreen(navController) }
            composable<Route.Memory> { MemoryScreen(navController) }
            composable<Route.Projects> { ProjectsScreen(navController) }
            composable<Route.Models> { ModelsScreen(navController) }
            composable<Route.Settings> { SettingsScreen(navController) }
            composable<Route.Skills> { SkillsScreen(navController) }
            composable<Route.Heartbeat> { HeartbeatScreen(navController) }
            composable<Route.Terminal> { TerminalScreen(navController) }
            composable<Route.Mcp> { McpScreen(navController) }
            composable<Route.Interactive> {
                InteractiveScreen(navController)
            }
        }
    }
}
