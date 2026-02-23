package com.immosnap

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.immosnap.pipeline.PipelineState
import com.immosnap.pipeline.PipelineViewModel
import com.immosnap.ui.camera.CameraScreen
import com.immosnap.ui.processing.ProcessingScreen
import com.immosnap.ui.result.ResultScreen

@Composable
fun ImmoSnapApp(viewModel: PipelineViewModel = viewModel()) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            is PipelineState.Processing -> navController.navigate("processing") {
                launchSingleTop = true
            }
            is PipelineState.Success, is PipelineState.Error -> navController.navigate("result") {
                launchSingleTop = true
            }
            else -> {}
        }
    }

    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                onPhotoTaken = { bitmap -> viewModel.onPhotoTaken(bitmap) },
                onGalleryPhoto = { bitmap, exifLocation -> viewModel.onGalleryPhotoSelected(bitmap, exifLocation) }
            )
        }
        composable("processing") {
            val processingState = state
            if (processingState is PipelineState.Processing) {
                ProcessingScreen(stepMessage = processingState.step)
            }
        }
        composable("result") {
            when (val resultState = state) {
                is PipelineState.Success -> ResultScreen(
                    results = resultState.results,
                    onRetry = {
                        viewModel.reset()
                        navController.popBackStack("camera", inclusive = false)
                    }
                )
                is PipelineState.Error -> ResultScreen(
                    results = emptyList(),
                    onRetry = {
                        viewModel.reset()
                        navController.popBackStack("camera", inclusive = false)
                    }
                )
                else -> {}
            }
        }
    }
}
