package com.immosnap.search

data class AddressInfo(
    val street: String?,
    val postalCode: String?,
    val city: String?
)

data class ListingCandidate(
    val title: String,
    val url: String,
    val snippet: String,
    val thumbnailUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val source: String
)
