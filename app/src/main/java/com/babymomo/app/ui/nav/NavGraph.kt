package com.babymomo.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
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
import kotlinx.serialization.Serializable

@Serializable data object ChatRoute
@Serializable data object MemoryRoute
@Serializable data object ProjectsRoute
@Serializable data object ModelsRoute
@Serializable data object SettingsRoute
@Serializable data object SkillsRoute
@Serializable data object HeartbeatRoute
@Serializable data object TerminalRoute
@Serializable data object McpRoute
@Serializable data class InteractiveRoute(val descriptor: String = "")

data class BottomNavItem(
    val route: Any,
    val label: String,
    val icon: ImageVector
)

@Composable
fun BabymomoNavHost() {
    val navController = rememberNavController()
    val bottomItems = listOf(
        BottomNavItem(ChatRoute, "Chat", Icons.Filled.Chat),
        BottomNavItem(MemoryRoute, "Memory", Icons.Filled.Memory),
        BottomNavItem(ProjectsRoute, "Projects", Icons.Filled.Folder),
        BottomNavItem(ModelsRoute, "Models", Icons.Filled.Storage),
        BottomNavItem(SettingsRoute, "Settings", Icons.Filled.Settings)
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
            startDestination = ChatRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<ChatRoute> { ChatScreen(navController) }
            composable<MemoryRoute> { MemoryScreen(navController) }
            composable<ProjectsRoute> { ProjectsScreen(navController) }
            composable<ModelsRoute> { ModelsScreen(navController) }
            composable<SettingsRoute> { SettingsScreen(navController) }
            composable<SkillsRoute> { SkillsScreen(navController) }
            composable<HeartbeatRoute> { HeartbeatScreen(navController) }
            composable<TerminalRoute> { TerminalScreen(navController) }
            composable<McpRoute> { McpScreen(navController) }
            composable<InteractiveRoute> { InteractiveScreen(navController) }
        }
    }
}
