package com.immosnap.pipeline

import com.immosnap.matching.MatchResult

sealed class PipelineState {
    data object Idle : PipelineState()
    data class Processing(val step: String) : PipelineState()
    data class Success(val results: List<MatchResult>) : PipelineState()
    data class Error(val message: String, val debugInfo: DebugInfo? = null) : PipelineState()
}

data class DebugInfo(
    val ocrText: String,
    val agencyName: String?,
    val refNumber: String?,
    val lat: Double?,
    val lng: Double?,
    val address: String?,
    val searchQuery: String,
    val locationSource: String
)
