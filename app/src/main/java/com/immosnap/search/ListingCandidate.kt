package com.immosnap.search

data class AddressInfo(
    val street: String?,
    val postalCode: String?,
    val city: String?,
    /**
     * Populated by [com.immosnap.search.GeocodingService] when reverse geocoding fails
     * (HTTP error, quota, malformed response). The address fields above stay null so the
     * pipeline can still fall back to OCR-only search, but this error message is plumbed
     * into [com.immosnap.pipeline.DebugInfo.searchError] so the failure is visible on the
     * result screen's debug card instead of being silently swallowed.
     */
    val error: String? = null
)

data class ListingCandidate(
    val title: String,
    val url: String,
    val snippet: String,
    val thumbnailUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val source: String
)
