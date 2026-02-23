package com.immosnap.pipeline

import android.content.Context
import android.graphics.Bitmap
import com.immosnap.location.LocationService
import com.immosnap.matching.ImageMatchService
import com.immosnap.matching.MatchResult
import com.immosnap.ocr.OcrService
import com.immosnap.search.GeocodingService
import com.immosnap.search.ListingSearchService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SnapPipeline(context: Context) {

    private val locationService = LocationService(context)
    private val ocrService = OcrService()
    private val geocodingService = GeocodingService()
    private val listingSearchService = ListingSearchService()
    private val imageMatchService = ImageMatchService()

    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state = _state.asStateFlow()

    suspend fun process(photo: Bitmap, exifLocation: Pair<Double, Double>? = null) {
        try {
            _state.value = PipelineState.Processing("Reading sign...")
            val signInfo = ocrService.extractText(photo)

            _state.value = PipelineState.Processing("Finding address...")
            val (lat, lng) = exifLocation ?: run {
                val loc = locationService.getCurrentLocation()
                loc.latitude to loc.longitude
            }
            val address = geocodingService.reverseGeocode(lat, lng)

            _state.value = PipelineState.Processing("Searching listings...")
            val candidates = listingSearchService.search(signInfo, address)

            if (candidates.isEmpty()) {
                _state.value = PipelineState.Error("No listings found nearby")
                return
            }

            _state.value = PipelineState.Processing("Matching photos...")
            val results = imageMatchService.rankCandidates(photo, candidates)

            _state.value = PipelineState.Success(results)
        } catch (e: Exception) {
            _state.value = PipelineState.Error(e.message ?: "Unknown error")
        }
    }

    fun reset() {
        _state.value = PipelineState.Idle
    }
}
