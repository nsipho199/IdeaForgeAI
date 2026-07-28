package com.ideaforge.ai.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ideaforge.ai.ui.screens.apks.ApksScreen
import com.ideaforge.ai.ui.screens.assistant.AssistantScreen
import com.ideaforge.ai.ui.screens.build.BuildScreen
import com.ideaforge.ai.ui.screens.home.HomeScreen
import com.ideaforge.ai.ui.screens.projects.ProjectsScreen
import com.ideaforge.ai.ui.screens.promptlibrary.PromptLibraryScreen
import com.ideaforge.ai.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector? = null) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Build : Screen("build/{idea}", "Build", Icons.Default.Build) {
        fun createRoute(idea: String) = "build/${java.net.URLEncoder.encode(idea, "UTF-8")}"
    }
    object Projects : Screen("projects", "Projects")
    object ProjectDetail : Screen("project/{projectId}", "Project Detail") {
        fun createRoute(projectId: String) = "project/$projectId"
    }
    object Apks : Screen("apks", "APKs")
    object PromptLibrary : Screen("prompt_library", "Prompts")
    object Assistant : Screen("assistant", "Assistant")
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Projects,
    Screen.Apks,
    Screen.PromptLibrary,
    Screen.Settings
)

@Composable
fun IdeaForgeNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                screen.icon?.let { Icon(it, contentDescription = screen.label) }
                            },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start, tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start, tween(300)
                )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End, tween(300)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End, tween(300)
                )
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onBuildApp = { idea ->
                        navController.navigate(Screen.Build.createRoute(idea))
                    },
                    onNavigateToPromptLibrary = {
                        navController.navigate(Screen.PromptLibrary.route)
                    },
                    onNavigateToAssistant = {
                        navController.navigate(Screen.Assistant.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(
                route = Screen.Build.route,
                arguments = listOf(
                    navArgument("idea") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val idea = backStackEntry.arguments?.getString("idea") ?: ""
                val decodedIdea = java.net.URLDecoder.decode(idea, "UTF-8")
                BuildScreen(
                    idea = decodedIdea,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Projects.route) {
                ProjectsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onProjectClick = { projectId ->
                        navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                    }
                )
            }

            composable(
                route = Screen.ProjectDetail.route,
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                ProjectsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onProjectClick = { id ->
                        navController.navigate(Screen.ProjectDetail.createRoute(id))
                    },
                    selectedProjectId = projectId
                )
            }

            composable(Screen.Apks.route) {
                ApksScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.PromptLibrary.route) {
                PromptLibraryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onUsePrompt = { prompt ->
                        navController.navigate(Screen.Build.createRoute(prompt))
                    }
                )
            }

            composable(Screen.Assistant.route) {
                AssistantScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
