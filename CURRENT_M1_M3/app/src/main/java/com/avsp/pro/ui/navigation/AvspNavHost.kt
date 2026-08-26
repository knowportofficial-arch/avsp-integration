package com.avsp.pro.ui.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.avsp.pro.audio.ui.AudioTtsScreen
import com.avsp.pro.audio.ui.AudioTtsViewModel
import com.avsp.pro.capture.camera.CameraPreviewScreen
import com.avsp.pro.capture.camera.CameraViewModel
import com.avsp.pro.capture.camera.CameraViewModelFactory
import com.avsp.pro.capture.camera.guided.GuidedCaptureActivity
import com.avsp.pro.di.AppContainer
import com.avsp.pro.media.MediaLibraryScreen
import com.avsp.pro.script.ui.ScriptAiScreen
import com.avsp.pro.script.ui.ScriptAiViewModel
import com.avsp.pro.ui.screens.home.HomeScreen
import com.avsp.pro.ui.screens.logs.LogsScreen
import com.avsp.pro.ui.screens.media.MediaScreen
import com.avsp.pro.ui.screens.modules.ModulesScreen
import com.avsp.pro.ui.screens.projects.ProjectDetailScreen
import com.avsp.pro.ui.screens.projects.ProjectsScreen
import com.avsp.pro.ui.screens.settings.SettingsScreen
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
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            val hideBottomBar = currentRoute?.startsWith("camera/") == true ||
                currentRoute?.startsWith("media_library/") == true ||
                currentRoute?.startsWith("script/") == true ||
                currentRoute?.startsWith("audio/") == true
            if (!hideBottomBar) {
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
                val guidedLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        vm.load(projectId)
                    }
                }
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
                    onOpenCamera = {
                        navController.navigate(AvspDestination.Camera.createRoute(projectId))
                    },
                    onOpenMediaLibrary = {
                        navController.navigate(AvspDestination.MediaLibrary.createRoute(projectId))
                    },
                    onOpenGuidedCapture = {
                        guidedLauncher.launch(
                            GuidedCaptureActivity.launchIntent(context, projectId)
                        )
                    }
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
                route = AvspDestination.Camera.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                val cameraFactory = CameraViewModelFactory(
                    container.mediaRepository,
                    container.captureProjectRepository
                )
                val cameraVm: CameraViewModel = viewModel(factory = cameraFactory)
                CameraPreviewScreen(
                    projectId = projectId,
                    viewModel = cameraVm,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = AvspDestination.MediaLibrary.route,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                MediaLibraryScreen(
                    projectId = projectId,
                    mediaRepository = container.mediaRepository,
                    onNavigateBack = { navController.popBackStack() }
                )
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
    AvspDestination.ProjectDetail,
    AvspDestination.ScriptAi,
    AvspDestination.AudioTts,
    AvspDestination.Camera,
    AvspDestination.MediaLibrary -> Icons.Filled.Folder
}
