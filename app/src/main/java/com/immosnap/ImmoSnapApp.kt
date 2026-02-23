package com.immosnap

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun ImmoSnapApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") { /* CameraScreen - Task 3 */ }
        composable("processing") { /* ProcessingScreen - Task 8 */ }
        composable("result") { /* ResultScreen - Task 8 */ }
    }
}
