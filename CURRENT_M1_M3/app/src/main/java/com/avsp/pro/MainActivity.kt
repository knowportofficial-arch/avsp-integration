package com.avsp.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.avsp.pro.capture.theme.AppPreferences
import com.avsp.pro.ui.navigation.AvspNavHost
import com.avsp.pro.ui.theme.AvspTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.initialize(this)
        enableEdgeToEdge()
        val app = application as AvspApplication
        setContent {
            AvspTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AvspNavHost(container = app.container)
                }
            }
        }
    }
}
