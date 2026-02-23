package com.immosnap.pipeline

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class PipelineViewModel(application: Application) : AndroidViewModel(application) {

    private val pipeline = SnapPipeline(application)
    val state = pipeline.state

    private var capturedPhoto: Bitmap? = null

    fun onPhotoTaken(bitmap: Bitmap) {
        capturedPhoto = bitmap
        viewModelScope.launch {
            pipeline.process(bitmap)
        }
    }

    fun retry() {
        capturedPhoto?.let { photo ->
            pipeline.reset()
            viewModelScope.launch { pipeline.process(photo) }
        }
    }

    fun reset() {
        capturedPhoto = null
        pipeline.reset()
    }
}
