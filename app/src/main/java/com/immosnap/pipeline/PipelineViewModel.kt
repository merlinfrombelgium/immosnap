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
    private var capturedExifLocation: Pair<Double, Double>? = null

    fun onPhotoTaken(bitmap: Bitmap) {
        capturedPhoto = bitmap
        capturedExifLocation = null
        viewModelScope.launch {
            pipeline.process(bitmap)
        }
    }

    fun onGalleryPhotoSelected(bitmap: Bitmap, exifLocation: Pair<Double, Double>?) {
        capturedPhoto = bitmap
        capturedExifLocation = exifLocation
        viewModelScope.launch {
            pipeline.process(bitmap, exifLocation)
        }
    }

    fun retry() {
        capturedPhoto?.let { photo ->
            pipeline.reset()
            viewModelScope.launch { pipeline.process(photo, capturedExifLocation) }
        }
    }

    fun reset() {
        capturedPhoto = null
        pipeline.reset()
    }
}
