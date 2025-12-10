package com.gamestore.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.gamestore.app.ui.screens.DatabaseManagerScreen
import com.gamestore.app.ui.screens.DownloadsScreen
import com.gamestore.app.ui.screens.GameDetailScreen
import com.gamestore.app.ui.screens.HomeScreen
import com.gamestore.app.ui.screens.PermissionScreen
import com.gamestore.app.ui.screens.PlatformDatabasesScreen
import com.gamestore.app.ui.screens.PlatformGamesScreen
import com.gamestore.app.ui.screens.SearchScreen
import com.gamestore.app.ui.screens.SettingsScreen
import com.gamestore.app.util.PermissionHelper

sealed class Screen(val route: String) {
    object Permission : Screen("permission")
    object Home : Screen("home")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Downloads : Screen("downloads")
    object DatabaseManager : Screen("database_manager")
    object PlatformDatabases : Screen("platform_databases/{platformName}") {
        fun createRoute(platformName: String) = "platform_databases/$platformName"
    }
    object PlatformGames : Screen("platform_games/{platformName}/{databaseName}") {
        fun createRoute(platformName: String, databaseName: String?) = 
            "platform_games/$platformName/${databaseName ?: "primary"}"
    }
    object GameDetail : Screen("game_detail/{gameId}") {
        fun createRoute(gameId: String) = "game_detail/$gameId"
    }
}

data class NavigationItem(
    val screen: Screen,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val navigationItems = listOf(
    NavigationItem(
        screen = Screen.Home,
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    NavigationItem(
        screen = Screen.Search,
        title = "Pesquisar",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    ),
    NavigationItem(
        screen = Screen.Settings,
        title = "Configurações",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val hasPermissions = PermissionHelper.hasStoragePermission(context) && 
        (PermissionHelper.hasNotificationPermission(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)

    val startDestination = if (hasPermissions) Screen.Home.route else Screen.Permission.route
    
    val showBottomBar = currentDestination?.route in listOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.Settings.route
    )
    
    val showTopBar = currentDestination?.route in listOf(
        Screen.Search.route,
        Screen.Settings.route
    )

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { 
                        Text(
                            text = when (currentDestination?.route) {
                                Screen.Search.route -> "Pesquisar"
                                Screen.Settings.route -> "Configurações"
                                else -> "GameFluxer"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { navController.navigate(Screen.Downloads.route) }
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Downloads",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    navigationItems.forEach { item ->
                        val isSelected = currentDestination?.hierarchy?.any { 
                            it.route == item.screen.route 
                        } == true
                        
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Permission.route) {
                PermissionScreen(
                    onPermissionsGranted = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Permission.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onGameClick = { gameId ->
                        navController.navigate(Screen.GameDetail.createRoute(gameId))
                    }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onGameClick = { gameId ->
                        navController.navigate(Screen.GameDetail.createRoute(gameId))
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToDatabaseManager = {
                        navController.navigate(Screen.DatabaseManager.route)
                    }
                )
            }

            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.DatabaseManager.route) {
                DatabaseManagerScreen(
                    onBackClick = { navController.popBackStack() },
                    onNavigateToPlatformGames = { platformName ->
                        navController.navigate(Screen.PlatformDatabases.createRoute(platformName))
                    }
                )
            }
            
            composable(
                route = Screen.PlatformDatabases.route,
                arguments = listOf(navArgument("platformName") { type = NavType.StringType })
            ) { backStackEntry ->
                val platformName = backStackEntry.arguments?.getString("platformName") ?: return@composable
                PlatformDatabasesScreen(
                    platformName = platformName,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToGames = { platform, database ->
                        navController.navigate(Screen.PlatformGames.createRoute(platform, database))
                    }
                )
            }

            composable(
                route = Screen.PlatformGames.route,
                arguments = listOf(
                    navArgument("platformName") { type = NavType.StringType },
                    navArgument("databaseName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val platformName = backStackEntry.arguments?.getString("platformName") ?: return@composable
                val databaseName = backStackEntry.arguments?.getString("databaseName")
                val actualDatabaseName = if (databaseName == "primary") null else databaseName
                
                PlatformGamesScreen(
                    platformName = if (actualDatabaseName != null) "$platformName:$actualDatabaseName" else platformName,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.GameDetail.route,
                arguments = listOf(navArgument("gameId") { type = NavType.StringType })
            ) { backStackEntry ->
                val gameId = backStackEntry.arguments?.getString("gameId") ?: return@composable
                GameDetailScreen(
                    gameId = gameId,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
