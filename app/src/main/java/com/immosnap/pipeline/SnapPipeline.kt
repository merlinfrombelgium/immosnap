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
        var debugLat: Double? = null
        var debugLng: Double? = null
        var debugAddress: String? = null
        var debugQuery: String = ""
        var debugLocationSource: String = "unknown"
        var debugOcrText: String = ""
        var debugAgencyName: String? = null
        var debugRefNumber: String? = null

        try {
            _state.value = PipelineState.Processing("Reading sign...")
            val signInfo = ocrService.extractText(photo)
            debugOcrText = signInfo.rawText
            debugAgencyName = signInfo.agencyName
            debugRefNumber = signInfo.referenceNumber

            _state.value = PipelineState.Processing("Finding address...")
            val (lat, lng) = if (exifLocation != null) {
                debugLocationSource = "EXIF"
                exifLocation
            } else {
                debugLocationSource = "GPS"
                val loc = locationService.getCurrentLocation()
                loc.latitude to loc.longitude
            }
            debugLat = lat
            debugLng = lng
            val address = geocodingService.reverseGeocode(lat, lng)
            debugAddress = listOfNotNull(address.street, address.postalCode, address.city).joinToString(", ")

            _state.value = PipelineState.Processing("Searching listings...")
            val searchResult = listingSearchService.search(signInfo, address)
            debugQuery = searchResult.query

            if (searchResult.candidates.isEmpty()) {
                val debug = DebugInfo(debugOcrText, debugAgencyName, debugRefNumber, debugLat, debugLng, debugAddress, debugQuery, debugLocationSource,
                    searchResults = searchResult.rawResults, searchError = searchResult.error)
                DebugReporter.send(debug)
                _state.value = PipelineState.Error("No listings found nearby", debug)
                return
            }

            _state.value = PipelineState.Processing("Matching photos...")
            val candidates = searchResult.candidates
            val results = imageMatchService.rankCandidates(photo, candidates, address)

            _state.value = PipelineState.Success(results)
        } catch (e: Exception) {
            val debug = DebugInfo(debugOcrText, debugAgencyName, debugRefNumber, debugLat, debugLng, debugAddress, debugQuery, debugLocationSource)
            DebugReporter.send(debug)
            _state.value = PipelineState.Error(e.message ?: "Unknown error", debug)
        }
    }

    fun reset() {
        _state.value = PipelineState.Idle
    }
}
