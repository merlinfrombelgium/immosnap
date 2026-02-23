package com.immosnap.pipeline

import com.immosnap.matching.MatchResult

sealed class PipelineState {
    data object Idle : PipelineState()
    data class Processing(val step: String) : PipelineState()
    data class Success(val results: List<MatchResult>) : PipelineState()
    data class Error(val message: String) : PipelineState()
}
