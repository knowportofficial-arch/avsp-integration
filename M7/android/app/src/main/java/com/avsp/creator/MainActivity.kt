package com.avsp.creator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.avsp.creator.core.navigation.NavGraph
import com.avsp.creator.core.theme.AVSPTheme
import com.avsp.creator.core.theme.AppPreferences
import com.avsp.creator.ui.dashboard.DashboardViewModel
import com.avsp.creator.ui.projects.ProjectViewModel

class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as AvspApplication
                return DashboardViewModel(app.projectRepository) as T
            }
        }
    }

    private val projectViewModel: ProjectViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as AvspApplication
                return ProjectViewModel(app.projectRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.initialize(this)

        setContent {
            AVSPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        dashboardViewModel = dashboardViewModel,
                        projectViewModel = projectViewModel
                    )
                }
            }
        }
    }
}
