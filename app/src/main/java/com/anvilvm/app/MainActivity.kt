package com.anvilvm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anvilvm.app.ui.terminal.TerminalScreen
import com.anvilvm.app.ui.display.VncDisplayScreen
import com.anvilvm.app.ui.vmstore.VMStoreScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnvilVMTheme {
                AnvilVMApp()
            }
        }
    }
}

@Composable
fun AnvilVMApp() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        navController.navigate("terminal") {
                            popUpTo("terminal") { inclusive = true }
                        }
                    },
                    label = { Text("Terminal") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        navController.navigate("display") {
                            popUpTo("terminal")
                        }
                    },
                    label = { Text("Display") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        navController.navigate("store") {
                            popUpTo("terminal")
                        }
                    },
                    label = { Text("Images") },
                    icon = {}
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "terminal",
            modifier = Modifier.padding(padding)
        ) {
            composable("terminal") { TerminalScreen() }
            composable("display") { VncDisplayScreen() }
            composable("store") { VMStoreScreen() }
        }
    }
}

@Composable
fun AnvilVMTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF00E5FF),
        secondary = androidx.compose.ui.graphics.Color(0xFFFF0055),
        background = androidx.compose.ui.graphics.Color(0xFF0A0B0D),
        surface = androidx.compose.ui.graphics.Color(0xFF1A1B1D),
        onPrimary = androidx.compose.ui.graphics.Color.Black,
        onBackground = androidx.compose.ui.graphics.Color.White,
        onSurface = androidx.compose.ui.graphics.Color.White
    )

    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}
