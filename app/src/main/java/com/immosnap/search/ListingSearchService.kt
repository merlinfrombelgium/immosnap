package com.immosnap.search

import com.immosnap.BuildConfig
import com.immosnap.ocr.SignInfo
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ListingSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class SearchResult(
        val candidates: List<ListingCandidate>,
        val query: String,
        val rawResults: List<String>,
        val error: String? = null
    )

    suspend fun search(signInfo: SignInfo, address: AddressInfo): SearchResult =
        withContext(Dispatchers.IO) {
            val queryParts = mutableListOf<String>()
            address.postalCode?.let { queryParts.add(it) }
            address.city?.let { queryParts.add(it) }
            signInfo.agencyName?.let { queryParts.add(it) }
            signInfo.referenceNumber?.let { queryParts.add(it) }
            if (address.postalCode == null && address.city == null) {
                signInfo.phoneNumber?.let { queryParts.add(it.replace(" ", "")) }
            }

            val searchTerms = queryParts.joinToString(" ")
            val prompt = """Find real estate listings for sale matching: $searchTerms
                |
                |Search on immoweb.be, zimmo.be, and immovlan.be.
                |
                |For each listing found, respond with a JSON array (no markdown):
                |[{"title": "...", "url": "...", "snippet": "..."}]
                |
                |Include the actual listing page URLs. If you find no specific listings, return [].
            """.trimMargin()

            val requestBody = buildJsonObject {
                put("contents", JsonArray(listOf(
                    buildJsonObject {
                        put("parts", JsonArray(listOf(
                            buildJsonObject { put("text", prompt) }
                        )))
                    }
                )))
                put("tools", JsonArray(listOf(
                    buildJsonObject {
                        put("google_search", buildJsonObject {})
                    }
                )))
            }.toString()

            try {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body!!.string()
                val json = Json.parseToJsonElement(body).jsonObject

                val error = json["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                if (error != null) {
                    return@withContext SearchResult(emptyList(), searchTerms, emptyList(), "Gemini error: $error")
                }

                val candidates = mutableListOf<ListingCandidate>()
                val rawResults = mutableListOf<String>()

                // Extract grounding chunks (these have real URLs)
                val groundingChunks = json["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("groundingMetadata")?.jsonObject
                    ?.get("groundingChunks")?.jsonArray

                groundingChunks?.forEach { chunk ->
                    val web = chunk.jsonObject["web"]?.jsonObject
                    if (web != null) {
                        val title = web["title"]?.jsonPrimitive?.content ?: ""
                        val uri = web["uri"]?.jsonPrimitive?.content ?: ""
                        rawResults.add("$title | $uri")

                        // Resolve redirect URLs to get the actual domain
                        val source = when {
                            "immoweb" in title.lowercase() || "immoweb" in uri -> "immoweb"
                            "zimmo" in title.lowercase() || "zimmo" in uri -> "zimmo"
                            "immovlan" in title.lowercase() || "immovlan" in uri -> "immovlan"
                            "immolot" in title.lowercase() || "immolot" in uri -> "immolot"
                            else -> "other"
                        }

                        // Only include real estate site results
                        if (source != "other") {
                            candidates.add(
                                ListingCandidate(
                                    title = title,
                                    url = uri,
                                    snippet = "",
                                    thumbnailUrl = null,
                                    source = source
                                )
                            )
                        }
                    }
                }

                // Also try to parse the text response for any additional URLs
                val textResponse = json["candidates"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content

                if (textResponse != null) {
                    rawResults.add(0, "Gemini response: ${textResponse.take(500)}")
                }

                SearchResult(candidates, searchTerms, rawResults)
            } catch (e: Exception) {
                SearchResult(emptyList(), searchTerms, emptyList(), "Search error: ${e.message}")
            }
        }
}
