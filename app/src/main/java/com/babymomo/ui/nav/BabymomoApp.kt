package com.babymomo.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.babymomo.R
import com.babymomo.ui.agents.AgentsScreen
import com.babymomo.ui.chat.ChatScreen
import com.babymomo.ui.memory.MemoryScreen
import com.babymomo.ui.models.ModelsScreen
import com.babymomo.ui.projects.ProjectsScreen
import com.babymomo.ui.settings.SettingsScreen
import com.babymomo.ui.skills.SkillsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabymomoApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: BabymomoDestination.START.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Column {
                    Text("BABYMOMO", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                    Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
                    BabymomoDestination.DRAWER_ITEMS.forEach { dest ->
                        NavigationDrawerItem(
                            label = { Text(dest.label) },
                            selected = currentRoute == dest.route,
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            onClick = {
                                navController.navigate(dest.route) { launchSingleTop = true; restoreState = true }
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors()
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(BabymomoDestination.entries.firstOrNull { it.route == currentRoute }?.label ?: "BABYMOMO", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Rounded.Menu, contentDescription = "Menu") } }
                )
            },
            bottomBar = {
                NavigationBar {
                    BabymomoDestination.BOTTOM_BAR_ITEMS.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { navController.navigate(dest.route) { popUpTo(BabymomoDestination.START.route) { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        ) { inner ->
            NavHost(navController = navController, startDestination = BabymomoDestination.START.route, modifier = Modifier.fillMaxSize().padding(inner)) {
                composable(BabymomoDestination.CHAT.route) { ChatScreen() }
                composable(BabymomoDestination.MEMORY.route) { MemoryScreen() }
                composable(BabymomoDestination.PROJECTS.route) { ProjectsScreen() }
                composable(BabymomoDestination.SKILLS.route) { SkillsScreen() }
                composable(BabymomoDestination.AGENTS.route) { AgentsScreen() }
                composable(BabymomoDestination.MODELS.route) { ModelsScreen() }
                composable(BabymomoDestination.SETTINGS.route) { SettingsScreen() }
            }
        }
    }
}
