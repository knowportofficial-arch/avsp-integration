package com.avsp.pro.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.avsp.pro.di.AppContainer
import com.avsp.pro.ui.screens.home.HomeScreen
import com.avsp.pro.ui.screens.logs.LogsScreen
import com.avsp.pro.ui.screens.media.MediaScreen
import com.avsp.pro.ui.screens.modules.ModulesScreen
import com.avsp.pro.ui.screens.projects.ProjectDetailScreen
import com.avsp.pro.ui.screens.projects.ProjectsScreen
import com.avsp.pro.ui.screens.settings.SettingsScreen
import com.avsp.pro.script.ui.ScriptAiScreen
import com.avsp.pro.script.ui.ScriptAiViewModel
import com.avsp.pro.audio.ui.AudioTtsScreen
import com.avsp.pro.audio.ui.AudioTtsViewModel
import com.avsp.pro.video.ui.VideoScreen
import com.avsp.pro.video.ui.VideoViewModel
import com.avsp.pro.ui.screens.pipeline.M5InputScreen
import com.avsp.pro.ui.screens.pipeline.M8CreativeScreen
import com.avsp.pro.ui.screens.pipeline.M9PublishingScreen
import com.avsp.pro.m7.capture.camera.CameraPreviewScreen
import com.avsp.pro.m7.capture.camera.CameraViewModel
import com.avsp.pro.m7.capture.camera.CameraViewModelFactory
import com.avsp.pro.m7.database.entity.ProjectEntity
import com.avsp.pro.m7.domain.model.Project as M7Project
import com.avsp.pro.m7.domain.model.ProjectStatus as M7ProjectStatus
import com.avsp.pro.ui.viewmodel.HomeViewModel
import com.avsp.pro.ui.viewmodel.LogsViewModel
import com.avsp.pro.ui.viewmodel.MediaViewModel
import com.avsp.pro.ui.viewmodel.ModulesViewModel
import com.avsp.pro.ui.viewmodel.ProjectDetailViewModel
import com.avsp.pro.ui.viewmodel.ProjectsViewModel
import com.avsp.pro.ui.viewmodel.SettingsViewModel
import com.avsp.pro.ui.viewmodel.ViewModelFactory

@Composable
fun AvspNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val factory = ViewModelFactory(container)
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                AvspDestination.bottomBar.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route ||
                            (dest == AvspDestination.Projects && currentRoute?.startsWith("project/") == true),
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(iconFor(dest), contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AvspDestination.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(AvspDestination.Home.route) {
                val vm: HomeViewModel = viewModel(factory = factory)
                HomeScreen(
                    viewModel = vm,
                    onOpenProjects = { navController.navigate(AvspDestination.Projects.route) },
                    onOpenModules = { navController.navigate(AvspDestination.Modules.route) },
                    onOpenSettings = { navController.navigate(AvspDestination.Settings.route) }
                )
            }
            composable(AvspDestination.Projects.route) {
                val vm: ProjectsViewModel = viewModel(factory = factory)
                ProjectsScreen(
                    viewModel = vm,
                    onOpenProject = { id ->
                        navController.navigate(AvspDestination.ProjectDetail.createRoute(id))
                    }
                )
            }
            composable(
                route = AvspDestination.ProjectDetail.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                val vm: ProjectDetailViewModel = viewModel(factory = factory)
                ProjectDetailScreen(
                    projectId = projectId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenScriptAi = {
                        navController.navigate(AvspDestination.ScriptAi.createRoute(projectId))
                    },
                    onOpenAudioTts = {
                        navController.navigate(AvspDestination.AudioTts.createRoute(projectId))
                    },
                    onOpenVideoEngine = {
                        navController.navigate(AvspDestination.VideoEngine.createRoute(projectId))
                    },
                    onOpenM5 = { navController.navigate(AvspDestination.M5Input.createRoute(projectId)) },
                    onOpenM6 = { navController.navigate(AvspDestination.M6Camera.createRoute(projectId)) },
                    onOpenM8 = { navController.navigate(AvspDestination.M8Creative.createRoute(projectId)) },
                    onOpenM9 = { navController.navigate(AvspDestination.M9Publishing.createRoute(projectId)) }
                )
            }
            composable(
                route = AvspDestination.ScriptAi.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                val vm: ScriptAiViewModel = viewModel(factory = factory)
                ScriptAiScreen(
                    projectId = projectId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenAudio = {
                        navController.navigate(AvspDestination.AudioTts.createRoute(projectId))
                    }
                )
            }
            composable(
                route = AvspDestination.AudioTts.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                val vm: AudioTtsViewModel = viewModel(factory = factory)
                AudioTtsScreen(
                    projectId = projectId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = AvspDestination.VideoEngine.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                val vm: VideoViewModel = viewModel(factory = factory)
                VideoScreen(
                    projectId = projectId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = AvspDestination.M5Input.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) {
                M5InputScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = AvspDestination.M6Camera.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                val app = LocalContext.current.applicationContext as com.avsp.pro.AvspApplication
                LaunchedEffect(projectId) {
                    val current = runCatching { container.projectRepository.openProject(projectId) }.getOrNull()
                    if (current != null) {
                        app.m7Services.projectRepository.saveProject(
                            M7Project(
                                id = current.projectId, title = current.name, topic = current.description,
                                category = "", targetAudience = "", targetPlatform = current.aspectRatio.label,
                                targetLanguage = current.language.code, status = M7ProjectStatus.CAPTURE,
                                createdAt = current.createdAt, updatedAt = current.updatedAt
                            )
                        )
                    }
                }
                val vm: CameraViewModel = viewModel(
                    factory = CameraViewModelFactory(app.m7Services.mediaRepository, app.m7Services.projectRepository)
                )
                CameraPreviewScreen(projectId = projectId, viewModel = vm, onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = AvspDestination.M8Creative.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                M8CreativeScreen(entry.arguments?.getString("projectId") ?: "", onBack = { navController.popBackStack() })
            }
            composable(
                route = AvspDestination.M9Publishing.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                M9PublishingScreen(entry.arguments?.getString("projectId") ?: "", onBack = { navController.popBackStack() })
            }

            composable(AvspDestination.Media.route) {
                val vm: MediaViewModel = viewModel(factory = factory)
                MediaScreen(viewModel = vm)
            }
            composable(AvspDestination.Modules.route) {
                val vm: ModulesViewModel = viewModel(factory = factory)
                ModulesScreen(viewModel = vm)
            }
            composable(AvspDestination.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(viewModel = vm)
            }
            composable(AvspDestination.Logs.route) {
                val vm: LogsViewModel = viewModel(factory = factory)
                LogsScreen(viewModel = vm)
            }
        }
    }
}

private fun iconFor(dest: AvspDestination): ImageVector = when (dest) {
    AvspDestination.Home -> Icons.Filled.Home
    AvspDestination.Projects -> Icons.Filled.Folder
    AvspDestination.Media -> Icons.Filled.VideoLibrary
    AvspDestination.Modules -> Icons.Filled.List
    AvspDestination.Settings -> Icons.Filled.Settings
    AvspDestination.Logs -> Icons.Filled.Info
    AvspDestination.ProjectDetail, AvspDestination.ScriptAi, AvspDestination.AudioTts, AvspDestination.VideoEngine -> Icons.Filled.Folder
    AvspDestination.M5Input -> Icons.Filled.List
    AvspDestination.M6Camera -> Icons.Filled.Folder
    AvspDestination.M8Creative -> Icons.Filled.Folder
    AvspDestination.M9Publishing -> Icons.Filled.Folder
}



