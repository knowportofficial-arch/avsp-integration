package com.avsp.pro.ui.navigation

sealed class AvspDestination(val route: String, val label: String) {
    data object Home : AvspDestination("home", "Home")
    data object Projects : AvspDestination("projects", "Projects")
    data object Media : AvspDestination("media", "Media")
    data object Modules : AvspDestination("modules", "Modules")
    data object Settings : AvspDestination("settings", "Settings")
    data object Logs : AvspDestination("logs", "Logs")
    data object ProjectDetail : AvspDestination("project/{projectId}", "Project") {
        fun createRoute(projectId: String) = "project/$projectId"
    }

    data object ScriptAi : AvspDestination("script/{projectId}", "Script AI") {
        fun createRoute(projectId: String) = "script/$projectId"
    }

    data object AudioTts : AvspDestination("audio/{projectId}", "Audio/TTS") {
        fun createRoute(projectId: String) = "audio/$projectId"
    }

    companion object {
        val bottomBar = listOf(Home, Projects, Media, Modules, Settings, Logs)
    }
}
