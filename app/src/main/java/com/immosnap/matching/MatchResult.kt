package com.immosnap.matching

import com.immosnap.search.ListingCandidate

data class MatchResult(
    val candidate: ListingCandidate,
    val confidence: Float,
    val reasoning: String
)
