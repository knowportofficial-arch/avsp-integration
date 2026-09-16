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

    data object VideoEngine : AvspDestination("video/{projectId}", "Video Engine") {
        fun createRoute(projectId: String) = "video/$projectId"
    }
    data object M5Input : AvspDestination("m5/{projectId}", "M5 Input") {
        fun createRoute(projectId: String) = "m5/$projectId"
    }
    data object M6Camera : AvspDestination("m6camera/{projectId}", "AI Camera") {
        fun createRoute(projectId: String) = "m6camera/$projectId"
    }
    data object M8Creative : AvspDestination("m8/{projectId}", "M8 Creative") {
        fun createRoute(projectId: String) = "m8/$projectId"
    }
    data object M9Publishing : AvspDestination("m9/{projectId}", "M9 Publishing") {
        fun createRoute(projectId: String) = "m9/$projectId"
    }

    companion object {
        val bottomBar = listOf(Home, Projects, Media, Modules, Settings, Logs)
    }
}
