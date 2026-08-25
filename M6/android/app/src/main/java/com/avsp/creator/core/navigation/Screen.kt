package com.avsp.creator.core.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Projects : Screen("projects")
    object CreateProject : Screen("create_project")
    object Settings : Screen("settings")

    object Workspace : Screen("workspace/{projectId}") {
        fun createRoute(projectId: String) = "workspace/$projectId"
    }

    object Camera : Screen("camera/{projectId}") {
        fun createRoute(projectId: String) = "camera/$projectId"
    }

    object MediaLibrary : Screen("media_library/{projectId}") {
        fun createRoute(projectId: String) = "media_library/$projectId"
    }
}
