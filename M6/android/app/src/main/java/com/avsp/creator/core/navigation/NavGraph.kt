package com.avsp.creator.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avsp.creator.AvspApplication
import com.avsp.creator.capture.camera.CameraPreviewScreen
import com.avsp.creator.capture.camera.CameraViewModel
import com.avsp.creator.capture.camera.CameraViewModelFactory
import com.avsp.creator.core.theme.*
import com.avsp.creator.ui.dashboard.DashboardScreen
import com.avsp.creator.ui.dashboard.DashboardViewModel
import com.avsp.creator.ui.projects.CreateProjectScreen
import com.avsp.creator.ui.projects.ProjectListScreen
import com.avsp.creator.ui.projects.ProjectViewModel
import com.avsp.creator.ui.settings.SettingsScreen
import com.avsp.creator.ui.workspace.MediaLibraryScreen
import com.avsp.creator.ui.workspace.ProjectWorkspaceScreen
import kotlinx.coroutines.delay

@Composable
fun NavGraph(
    navController: NavHostController,
    dashboardViewModel: DashboardViewModel,
    projectViewModel: ProjectViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as AvspApplication

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onSplashFinished = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Home.route) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToProjects = { navController.navigate(Screen.Projects.route) },
                onNavigateToCreateProject = { navController.navigate(Screen.CreateProject.route) },
                onNavigateToWorkspace = { id -> navController.navigate(Screen.Workspace.createRoute(id)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Projects.route) {
            ProjectListScreen(
                viewModel = projectViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateProject = { navController.navigate(Screen.CreateProject.route) },
                onNavigateToWorkspace = { id -> navController.navigate(Screen.Workspace.createRoute(id)) }
            )
        }

        composable(Screen.CreateProject.route) {
            CreateProjectScreen(
                viewModel = projectViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProjectCreated = { newId ->
                    navController.navigate(Screen.Workspace.createRoute(newId)) {
                        popUpTo(Screen.CreateProject.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Workspace.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            ProjectWorkspaceScreen(
                projectId = projectId,
                viewModel = projectViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCamera = { id -> navController.navigate(Screen.Camera.createRoute(id)) },
                onNavigateToMediaLibrary = { id -> navController.navigate(Screen.MediaLibrary.createRoute(id)) }
            )
        }

        composable(
            route = Screen.Camera.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val cameraViewModel: CameraViewModel = viewModel(
                factory = CameraViewModelFactory(app.mediaRepository, app.projectRepository)
            )
            CameraPreviewScreen(
                projectId = projectId,
                viewModel = cameraViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MediaLibrary.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            MediaLibraryScreen(
                projectId = projectId,
                mediaRepository = app.mediaRepository,
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

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AVSP",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = Indigo500
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI Video Studio Pro",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Slate100
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Android Foundation v0.1",
                fontSize = 12.sp,
                color = Slate400
            )
        }
    }
}
